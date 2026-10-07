package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.block.entity.TubeBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import com.example.tiangongkaiwu.block.entity.JianBlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.ArrayDeque;

/**
 * 筒·滑轮形态（D16）：独立放置的流体机器，与世界批量汲泻。
 *
 * <p>（筒按在梘段上则是**泵形态**——提水上山，逻辑在 {@link JianBlock} 的 TUBE 附着态；
 * 一个筒物品，两种安装位即两种模式。）
 *
 * <p>四模式（空手右键循环汲/泻，机器劲档位「不及则慢」）：
 * <ul>
 * <li>**汲·单格**：邻侧世界源方块 → 邻梘段 +1000mB（守恒，1格=1B）；</li>
 * <li>**泻·单格**：邻梘段 −1000mB → 世界造源方块（下方>水平>上方）；</li>
 * <li>**汲·无限**：邻侧连通水体批量抽取——&lt;200 整抽消散一次到底 / 200~10000 逐步 /
 *     ≥10000 判「无限供应」抽取不消耗（三层判定，Create 同款）；**水被销毁**；</li>
 * <li>**泻·无限**：邻梘库存灌满连通空气区（≤200 格，逐格扣 1000mB）——守恒。</li>
 * </ul>
 */
public class TubeBlock extends Block implements EntityBlock {

    /** false=汲（进水） true=泻（出水）。 */
    public static final BooleanProperty DRAINING = BooleanProperty.create("draining");

    /** 结算周期（tick）。 */
    public static final int INTERVAL = 20;
    /** 连通体「小水体」阈值：≤ 此值一次整抽消散（D14 迁移）。 */
    public static final int SMALL_BODY = 200;
    /** 连通体「无限供应」阈值：≥ 此值抽取不消耗、排放直接消散（Create 同款）。 */
    public static final int INFINITE_BODY = 10000;
    /** 泻·无限/大水体逐格工作的半径。 */
    public static final int WORK_RADIUS = 5;
    /** 每次转移量（mB）= 1 个源方块。 */
    public static final int PER_BLOCK_MB = 1000;

    public TubeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(DRAINING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DRAINING);
    }

    /** 空手/任意右键：切换汲⇄泻（模式切换优先于贴方块放置）。 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        boolean nowDraining = !state.getValue(DRAINING);
        level.setBlock(pos, state.setValue(DRAINING, nowDraining), 3);
        if (player != null) {
            player.displayClientMessage(Component.translatable(nowDraining
                    ? "block.tiangongkaiwu.tong.mode_drain" : "block.tiangongkaiwu.tong.mode_draw"), true);
        }
        level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.5F, 1.1F);
        return ItemInteractionResult.sidedSuccess(false);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, INTERVAL);
        }
    }

    /** 邻侧有梘段吗（汲单/泻单/泻无限需要连网）。 */
    private static boolean nearJian(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (level.getBlockState(pos.relative(dir)).getBlock() instanceof JianBlock) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 机器劲档位：满劲每周期干活，2劲 2/3 概率，1劲 1/3（「不及则慢」，D16）
        int jin = PowerNetwork.allocate(level, pos, PowerNetwork.DEFAULT_DEMAND);
        if (jin <= 0 || random.nextInt(3) >= jin) {
            level.scheduleTick(pos, this, INTERVAL);
            return;
        }
        boolean draining = state.getValue(DRAINING);
        if (draining) {
            if (nearJian(level, pos)) {
                drainSingle(level, pos); // 泻·单格（守恒）
            } else {
                fillUnlimited(level, pos); // 泻·无限（梘库存灌区，守恒）
            }
        } else {
            if (nearJian(level, pos)) {
                fillSingle(level, pos); // 汲·单格（守恒）
            } else {
                drainUnlimited(level, pos, random); // 汲·无限（销毁，排涝）
            }
        }
        level.scheduleTick(pos, this, INTERVAL);
    }

    // ==================== 汲·单格 ====================

    private void fillSingle(ServerLevel level, BlockPos pos) {
        // 邻侧世界源
        for (Direction dir : Direction.values()) {
            BlockPos p = pos.relative(dir);
            FluidState fs = level.getFluidState(p);
            if (!fs.isSource() || fs.isEmpty()) {
                continue;
            }
            // 邻梘段接收（同网守恒：段满时梯度会匀给全网）
            for (Direction jd : Direction.values()) {
                BlockPos jp = pos.relative(jd);
                if (level.getBlockEntity(jp) instanceof JianBlockEntity jbe
                        && jbe.accept(fs.getType(), PER_BLOCK_MB) >= PER_BLOCK_MB) {
                    level.removeBlock(p, false);
                    return;
                }
            }
            return; // 有源但网收不下：不消费
        }
    }

    // ==================== 泻·单格 ====================

    private void drainSingle(ServerLevel level, BlockPos pos) {
        // 邻梘段排水
        for (Direction dir : Direction.values()) {
            BlockPos jp = pos.relative(dir);
            if (level.getBlockEntity(jp) instanceof JianBlockEntity jbe && jbe.getMb() >= PER_BLOCK_MB) {
                // 世界空位：下方 > 水平 > 上方
                BlockPos spot = findPlaceableSpot(level, pos);
                if (spot == null) {
                    return;
                }
                FluidStack drained = jbe.getFluidHandler().drain(PER_BLOCK_MB,
                        net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                if (drained.getAmount() >= PER_BLOCK_MB) {
                    level.setBlock(spot, drained.getFluid().defaultFluidState().createLegacyBlock(), 3);
                }
                return;
            }
        }
    }

    /** 找一个可以放源方块的空位（下方 > 水平 > 上方）。 */
    private static BlockPos findPlaceableSpot(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        if (level.getBlockState(below).isAir()) {
            return below;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos p = pos.relative(dir);
            if (level.getBlockState(p).isAir()) {
                return p;
            }
        }
        BlockPos above = pos.above();
        if (level.getBlockState(above).isAir()) {
            return above;
        }
        return null;
    }

    // ==================== 汲·无限（销毁/排涝） ====================

    private void drainUnlimited(ServerLevel level, BlockPos pos, RandomSource random) {
        // 从六邻找首个流体源，洪水填充计数
        for (Direction dir : Direction.values()) {
            BlockPos start = pos.relative(dir);
            FluidState fs = level.getFluidState(start);
            if (!fs.isSource() || fs.isEmpty()) {
                continue;
            }
            ObjectOpenHashSet<BlockPos> body = collectBody(level, start, fs, SMALL_BODY + 1);
            if (body.size() <= SMALL_BODY) {
                // 小水体：整抽消散；抽干格下是可作田的土 → 沉降一格涸田
                // （批 4 排涝造田，书：「去澤水以便栽種」）
                for (BlockPos p : body) {
                    level.removeBlock(p, false);
                    if (RiceStalkBlock.isPaddySoil(level.getBlockState(p.below()))) {
                        level.setBlock(p, TiangongKaiwu.HE_TIAN.get().defaultBlockState(), 3);
                    }
                }
            } else {
                // 大水体：只抽半径内的一格（抽不干）
                BlockPos target = null;
                for (BlockPos p : body) {
                    if (Math.abs(p.getX() - pos.getX()) <= WORK_RADIUS
                            && Math.abs(p.getY() - pos.getY()) <= WORK_RADIUS
                            && Math.abs(p.getZ() - pos.getZ()) <= WORK_RADIUS) {
                        target = p;
                        break;
                    }
                }
                if (target != null) {
                    level.removeBlock(target, false);
                }
            }
            return;
        }
    }

    /** 洪水填充收集同流体源连通体；超过 cap 停止（返回含 cap+1 个 = 判大水体）。 */
    private static ObjectOpenHashSet<BlockPos> collectBody(ServerLevel level, BlockPos start,
                                                           FluidState fs, int cap) {
        ObjectOpenHashSet<BlockPos> visited = new ObjectOpenHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty() && visited.size() <= cap) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (visited.add(n)) {
                    FluidState nfs = level.getFluidState(n);
                    if (nfs.isSource() && !nfs.isEmpty() && nfs.getType().isSame(fs.getType())) {
                        queue.add(n);
                    }
                }
                if (visited.size() > cap) {
                    return visited;
                }
            }
        }
        return visited;
    }

    // ==================== 泻·无限（梘库存灌区） ====================

    private void fillUnlimited(ServerLevel level, BlockPos pos) {
        // 找有水的邻梘段作水源
        for (Direction dir : Direction.values()) {
            BlockPos jp = pos.relative(dir);
            if (!(level.getBlockEntity(jp) instanceof JianBlockEntity jbe) || jbe.getMb() < PER_BLOCK_MB) {
                continue;
            }
            // 从邻空格洪水填充连通空气区（≤200 格），逐格扣库存造源
            ObjectOpenHashSet<BlockPos> area = new ObjectOpenHashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            for (Direction d : Direction.values()) {
                BlockPos n = pos.relative(d);
                if (level.getBlockState(n).isAir() && area.add(n)) {
                    queue.add(n);
                }
            }
            while (!queue.isEmpty() && area.size() <= SMALL_BODY) {
                BlockPos p = queue.poll();
                if (jbe.drainMb(PER_BLOCK_MB) < PER_BLOCK_MB) {
                    return; // 梘干即停
                }
                level.setBlock(p, jbe.getFluid().getFluid().defaultFluidState().createLegacyBlock(), 3);
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    if (level.getBlockState(n).isAir() && area.add(n)) {
                        queue.add(n);
                    }
                }
            }
            return;
        }
    }

    // ==================== BE / 掉落 / 碰撞 ====================

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TubeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (type != TiangongKaiwu.TUBE_BE_TYPE.get()) {
            return null;
        }
        return (lv, p, st, be) -> ((TubeBlockEntity) be).refreshStats();
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        Block.popResource(level, pos, new ItemStack(TiangongKaiwu.TUBE_ITEM.get()));
    }

    /** 薄壁筒碰撞：外径 12。 */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
    }

    /**
     * 筒物品：右键梘段 = 安装（泵形态，附着在该段上，物品 −1）；
     * 其余 = 普通放置（滑轮形态）。
     */
    public static class TubeItem extends BlockItem {

        public TubeItem(Block block, Properties properties) {
            super(block, properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof JianBlock && !state.getValue(JianBlock.TUBE)) {
                Player player = context.getPlayer();
                if (level.isClientSide) {
                    return InteractionResult.SUCCESS;
                }
                level.setBlock(pos, state.setValue(JianBlock.TUBE, true), 3);
                context.getItemInHand().shrink(1);
                if (player != null) {
                    player.displayClientMessage(Component.translatable(
                            "block.tiangongkaiwu.tong.installed"), true);
                }
                level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.6F, 1.0F);
                return InteractionResult.CONSUME;
            }
            return super.useOn(context);
        }
    }
}
