package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.block.entity.JianBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

import net.neoforged.neoforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

/**
 * 梘（引水槽）——D16 连通器重做：**梘是渠，不是容器**。
 *
 * <p>水存于每段 BE 的 FluidStack（mB 计量，**1 源方块 = 1B = 1000mB = 1 段满**），
 * 「级」抽象已退役。全网总量守恒，规则：
 * <ul>
 *   <li>**重力转移**：邻段水位比我高 ≥500mB → 它向我转 250mB（缓慢均衡）；
 *       正上方梘段 → 直落 250mB（向上永不流，D7）；</li>
 *   <li>**汲水**：正下方是世界流体源 → 每周期渗入 250mB（明渠浸水，渐满）；</li>
 *   <li>**灌田**：按 mB 预算漫灌，1 点田水 = 250mB；</li>
 *   <li>**泵（TUBE 附着）**：筒按在段上，吃劲把水从低处搬到劲去向——水不能上山，
 *       筒以劲提之（D16）。劲来向 = 邻侧的杆/动力源，水顺劲而行。</li>
 * </ul>
 *
 * <p>车系直喂已删除（D16 拆分）：桔槔/辘轳保留直喂（井具本职提水）。
 * 流体种类存 BE（{@link JianBlockEntity}），对外暴露标准 IFluidHandler（D9）；
 * 槽内水体由客户端 BER 按填充率动态渲染（D12）。
 */
public class JianBlock extends Block implements EntityBlock {

    /** 四向邻接（有相邻梘段则该侧开口），供 multipart 模型成型。 */
    public static final BooleanProperty CONNECT_NORTH = BooleanProperty.create("connect_north");
    public static final BooleanProperty CONNECT_SOUTH = BooleanProperty.create("connect_south");
    public static final BooleanProperty CONNECT_EAST = BooleanProperty.create("connect_east");
    public static final BooleanProperty CONNECT_WEST = BooleanProperty.create("connect_west");
    /**
     * 四向隔板（墨斗点侧面插/拆）：该侧封死——水不互通、墙板闭合。
     * 插板联动隔壁梘的对侧（一块板隔两家）。
     */
    public static final BooleanProperty SEAL_NORTH = BooleanProperty.create("seal_north");
    public static final BooleanProperty SEAL_SOUTH = BooleanProperty.create("seal_south");
    public static final BooleanProperty SEAL_EAST = BooleanProperty.create("seal_east");
    public static final BooleanProperty SEAL_WEST = BooleanProperty.create("seal_west");
    /** 泵附着（筒按在该段上，D16）：有劲则把水从低处搬向劲去向。 */
    public static final BooleanProperty TUBE = BooleanProperty.create("tube");

    /** 结算周期（tick）。 */
    public static final int INTERVAL = 10;
    /** 邻居变化后的快速重算延迟。 */
    public static final int REACT_DELAY = 2;
    /** 补水节奏：每次结算 1/4 概率。 */
    private static final int IRRIGATE_CHANCE = 4;

    /** 单次重力转移量（mB）。 */
    private static final int TRANSFER_MB = 250;
    /** 触发水平均衡的水位差（mB）。 */
    private static final int LEVEL_DIFF = 500;
    /** 1 点田水的代价（mB）。 */
    public static final int MB_PER_MOISTURE = 250;
    /** 每段容量（mB），与 {@link JianBlockEntity#SEGMENT_CAPACITY} 一致。 */
    public static final int SEGMENT_MB = 1000;

    public JianBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(CONNECT_NORTH, false)
                .setValue(CONNECT_SOUTH, false)
                .setValue(CONNECT_EAST, false)
                .setValue(CONNECT_WEST, false)
                .setValue(SEAL_NORTH, false)
                .setValue(SEAL_SOUTH, false)
                .setValue(SEAL_EAST, false)
                .setValue(SEAL_WEST, false)
                .setValue(TUBE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONNECT_NORTH, CONNECT_SOUTH, CONNECT_EAST, CONNECT_WEST,
                SEAL_NORTH, SEAL_SOUTH, SEAL_EAST, SEAL_WEST, TUBE);
    }

    /** 方向 → 该侧隔板属性。 */
    public static BooleanProperty sealFor(Direction dir) {
        return switch (dir) {
            case NORTH -> SEAL_NORTH;
            case SOUTH -> SEAL_SOUTH;
            case EAST -> SEAL_EAST;
            case WEST -> SEAL_WEST;
            default -> SEAL_NORTH; // 垂直向无隔板（不该到这）
        };
    }

    /**
     * 墨斗插/拆隔板：目标侧取反，并**联动**隔壁梘的对侧同插同拆（一块板隔两家）。
     */
    public static void toggleSeal(ServerLevel level, BlockPos pos, Direction side) {
        BlockState state = level.getBlockState(pos);
        boolean now = !state.getValue(sealFor(side));
        level.setBlock(pos, state.setValue(sealFor(side), now), 3);
        BlockPos np = pos.relative(side);
        BlockState ns = level.getBlockState(np);
        if (ns.getBlock() instanceof JianBlock) {
            level.setBlock(np, ns.setValue(sealFor(side.getOpposite()), now), 3);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, INTERVAL);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, REACT_DELAY);
        }
    }

    // ============================================================
    // 结算：泵 / 重力转移 / 汲水 / 灌田 / 邻接成型
    // ============================================================

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof JianBlockEntity jbe)) {
            return; // 无 BE 不结算
        }

        // ① 泵（TUBE 附着）：劲推水上山——从低处段搬向劲去向段
        if (state.getValue(TUBE)) {
            int jin = PowerNetwork.allocate(level, pos, PowerNetwork.DEFAULT_DEMAND);
            if (jin > 0 && pumpOnce(level, pos, random)) {
                level.playSound(null, pos, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.4F, 1.6F);
            }
        }

        int myMb = jbe.getMb();

        // ② 重力转移（局部连通器，每周期只收一笔）
        // ②a 直落：正上方段的水往下落
        BlockPos above = pos.above();
        if (myMb <= SEGMENT_MB - TRANSFER_MB
                && level.getBlockEntity(above) instanceof JianBlockEntity ube
                && ube.getMb() >= TRANSFER_MB) {
            if (JianBlock.transfer(level, above, pos, TRANSFER_MB)) {
                level.scheduleTick(above, this, INTERVAL);
            }
        }
        // ②b 水平均衡：邻段比我高 ≥LEVEL_DIFF → 它向我转一笔
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos p = pos.relative(dir);
            if (level.getBlockEntity(p) instanceof JianBlockEntity nbe
                    && nbe.getMb() - jbe.getMb() >= LEVEL_DIFF) {
                if (JianBlock.transfer(level, p, pos, TRANSFER_MB)) {
                    level.scheduleTick(p, this, INTERVAL);
                }
                break; // 每周期只收一笔
            }
        }

        int myMb2 = jbe.getMb();

        // ③ 汲水：正下方是世界流体源（或井具在提水——桔槔/辘轳本职直喂）→ 每周期渗入 250mB
        FluidState below = level.getFluidState(pos.below());
        boolean wellFeeding = WaterDevices.isRunning(level.getBlockState(pos.below()));
        if ((below.isSource() && !below.isEmpty()) || wellFeeding) {
            FluidState source = below.isSource() && !below.isEmpty() ? below : Fluids.WATER.defaultFluidState();
            if (myMb2 < SEGMENT_MB) {
                jbe.accept(source.getType(), TRANSFER_MB);
            }
        }

        // ④ 灌田（漫灌）：mB 预算，1 点田水 250mB；只灌水（熔岩段不灌）
        if (jbe.getMb() >= MB_PER_MOISTURE && !jbe.getFluid().isEmpty()
                && jbe.getFluid().getFluid().isSame(Fluids.WATER)
                && random.nextInt(IRRIGATE_CHANCE) == 0) {
            int spent = floodIrrigate(level, pos, jbe.getMb());
            if (spent > 0) {
                jbe.drainMb(spent);
            }
        }

        // ⑤ 邻接成型：两侧都没隔板才开口（隔板=墙板闭合）
        boolean cn = canConnect(state, level, pos, Direction.NORTH);
        boolean cs = canConnect(state, level, pos, Direction.SOUTH);
        boolean ce = canConnect(state, level, pos, Direction.EAST);
        boolean cw = canConnect(state, level, pos, Direction.WEST);

        BlockState newState = state
                .setValue(CONNECT_NORTH, cn).setValue(CONNECT_SOUTH, cs)
                .setValue(CONNECT_EAST, ce).setValue(CONNECT_WEST, cw);
        if (newState != state) {
            level.setBlock(pos, newState, 2);
        }

        level.scheduleTick(pos, this, INTERVAL);
    }

    /**
     * 泵一次工作（D16）：劲来向 = 邻侧的杆/动力源；水顺劲而行——
     * 从其余邻段中水位最高者搬 {@link #SEGMENT_MB} 到劲去向的段。
     *
     * @return 是否搬成功
     */
    private boolean pumpOnce(ServerLevel level, BlockPos pos, RandomSource random) {
        Direction inDir = null;
        for (Direction d : Direction.values()) {
            BlockState nb = level.getBlockState(pos.relative(d));
            if (nb.getBlock() instanceof ShaftBlock || WaterDevices.emittedPower(nb) > 0) {
                inDir = d;
                break;
            }
        }
        if (inDir == null) {
            return false; // 劲不经轴来（隔空连网），无从定向
        }
        Direction outDir = inDir.getOpposite();
        BlockPos outPos = pos.relative(outDir);
        BlockPos best = null;
        int bestMb = -1;
        for (Direction d : Direction.values()) {
            if (d == outDir) {
                continue;
            }
            BlockPos p = pos.relative(d);
            if (level.getBlockEntity(p) instanceof JianBlockEntity nbe
                    && nbe.getMb() >= SEGMENT_MB && nbe.getMb() > bestMb) {
                bestMb = nbe.getMb();
                best = p;
            }
        }
        if (best == null) {
            return false;
        }
        return JianBlock.transfer(level, best, outPos, SEGMENT_MB);
    }

    /** 段间转移：同流体、目标容量内；成功返回 true。 */
    public static boolean transfer(ServerLevel level, BlockPos from, BlockPos to, int amount) {
        if (from.equals(to) || amount <= 0) {
            return false;
        }
        if (!(level.getBlockEntity(from) instanceof JianBlockEntity fbe)
                || !(level.getBlockEntity(to) instanceof JianBlockEntity tbe)) {
            return false;
        }
        FluidStack f = fbe.getFluid();
        if (f.isEmpty() || f.getAmount() < amount) {
            return false;
        }
        return tbe.accept(f.getFluid(), amount) >= amount;
    }

    /** 该侧能否开口：邻格是梘、我没插隔板、对面也没插隔板。 */
    private static boolean canConnect(BlockState state, ServerLevel level, BlockPos pos, Direction dir) {
        if (state.getValue(sealFor(dir))) {
            return false;
        }
        BlockState nb = level.getBlockState(pos.relative(dir));
        return nb.getBlock() instanceof JianBlock && !nb.getValue(sealFor(dir.getOpposite()));
    }

    /**
     * 漫灌：从槽段四邻出发，沿相邻稻秆/枯秆洪水填充整片连体田。
     *
     * <p>预算 = 传入 mB（1 点田水 = {@link #MB_PER_MOISTURE}）；近处优先（BFS 天然按距离序），
     * 预算耗尽即停；一整轮下来一块田都没浇到（预算没花出去）则不扣水。
     * 访问上限 256 格，防超大连体田卡结算。
     *
     * @return 实际花掉的 mB
     */
    private int floodIrrigate(ServerLevel level, BlockPos pos, int budget) {
        it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<BlockPos> visited =
                new it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos p = pos.relative(dir);
            if (visited.add(p)) {
                queue.add(p);
            }
        }
        int spent = 0;
        while (!queue.isEmpty() && spent < budget && visited.size() < 256) {
            BlockPos p = queue.poll();
            BlockState s = level.getBlockState(p);
            if (s.getBlock() == TiangongKaiwu.RICE_STALK.get()) {
                int moisture = s.getValue(RiceStalkBlock.MOISTURE);
                int deficit = RiceStalkBlock.MAX_MOISTURE - moisture;
                int affordable = Math.min(deficit, (budget - spent) / MB_PER_MOISTURE);
                if (affordable > 0) {
                    level.setBlock(p, RiceStalkBlock.withMoisture(s, moisture + affordable), 2);
                    spent += affordable * MB_PER_MOISTURE;
                }
            } else if (s.getBlock() == TiangongKaiwu.RICE_STALK_DRY.get()) {
                if (budget - spent >= MB_PER_MOISTURE) {
                    level.setBlock(p, RiceStalkBlockDry.reviveState(s), 3);
                    spent += MB_PER_MOISTURE;
                }
            } else {
                continue; // 非田块：不向外扩散（漫灌沿田走，不入空气）
            }
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(dir);
                if (visited.add(n)) {
                    BlockState ns = level.getBlockState(n);
                    if (ns.getBlock() == TiangongKaiwu.RICE_STALK.get()
                            || ns.getBlock() == TiangongKaiwu.RICE_STALK_DRY.get()) {
                        queue.add(n);
                    }
                }
            }
        }
        return spent; // 只实浇才扣水（0 = 没花出去）
    }

    // ============================================================
    // 交互：拆泵 / BE
    // ============================================================

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty() || !state.getValue(TUBE)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        // 空手右键：拆下泵（筒退还）
        level.setBlock(pos, state.setValue(TUBE, false), 3);
        Block.popResource(level, pos, new ItemStack(TiangongKaiwu.TUBE_ITEM.get()));
        level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.6F, 0.9F);
        return ItemInteractionResult.sidedSuccess(false);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new JianBlockEntity(pos, state);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        popResource(level, pos, new ItemStack(TiangongKaiwu.JIAN_ITEM.get()));
    }

    // ====== 碰撞箱（浅槽：低位碰撞，能踩过去）======
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D);
    }
}
