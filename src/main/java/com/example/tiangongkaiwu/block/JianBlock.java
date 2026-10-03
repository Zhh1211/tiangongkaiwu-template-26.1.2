package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.block.entity.JianBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.neoforged.neoforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

/**
 * 梘（引水槽）——批 2 流体化重做（D7/D11/D12）。
 *
 * <p>水位 = {@code LEVEL(0-7) × SUB(0/1)} 共 16 个有效档（eff = LEVEL*2+SUB），
 * 纯局部规则传播：
 * <ul>
 *   <li>正下方有水（或正在转的取水装置）→ 自己灌满（汲水）；</li>
 *   <li>水平邻梘的水位比我高 ≥2 → 我涨 1 级（水位差保持 1 的压力梯度，
 *       每两格降一级、平地 16 格，见 D12）；</li>
 *   <li>正下方是梘 → 直落给它（垂直方向不衰减），向上永不流；</li>
 *   <li>有水时按概率给四邻稻田补水，<b>耗自己 1 级水位</b>（灌田有价）。</li>
 * </ul>
 *
 * <p>去 FACING 自动邻接（D7）：槽没有朝向，水往哪流全看水位差。
 * 保留 FACING/WATERED 两个遗留属性仅为旧档自愈（旧梘 watered=true → 迁移为满水），
 * 迁移在首次 tick 完成后归零，玩家无感。
 *
 * <p>流体种类存 BE（{@link JianBlockEntity}），对外暴露标准 IFluidHandler（D9）；
 * 槽内水体由客户端 BER 动态渲染（按所装流体取贴图染色，D12）。
 */
public class JianBlock extends Block implements EntityBlock {

    /** 高 3 位水位（0-7）。 */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 7);
    /** 半级位（0/1）——与 LEVEL 合成 16 个有效档。 */
    public static final IntegerProperty SUB = IntegerProperty.create("sub", 0, 1);
    /** 四向邻接（有相邻梘段则该侧开口），供 multipart 模型成型。 */
    public static final BooleanProperty CONNECT_NORTH = BooleanProperty.create("connect_north");
    public static final BooleanProperty CONNECT_SOUTH = BooleanProperty.create("connect_south");
    public static final BooleanProperty CONNECT_EAST = BooleanProperty.create("connect_east");
    public static final BooleanProperty CONNECT_WEST = BooleanProperty.create("connect_west");

    // ===== 遗留属性：仅为旧档自愈，新逻辑不使用 =====
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERED = BooleanProperty.create("watered");

    /** 结算周期（tick）。 */
    public static final int INTERVAL = 10;
    /** 邻居变化后的快速重算延迟。 */
    public static final int REACT_DELAY = 2;
    /** 补水节奏：每次结算 1/4 概率 → 平均 2 秒补 1 格，同时耗自己 1 级水位。 */
    private static final int IRRIGATE_CHANCE = 4;

    /** 满槽有效水位。 */
    public static final int MAX_EFF = 15;

    public JianBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(LEVEL, 0)
                .setValue(SUB, 0)
                .setValue(CONNECT_NORTH, false)
                .setValue(CONNECT_SOUTH, false)
                .setValue(CONNECT_EAST, false)
                .setValue(CONNECT_WEST, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL, SUB, CONNECT_NORTH, CONNECT_SOUTH, CONNECT_EAST, CONNECT_WEST,
                FACING, WATERED);
    }

    /** 有效水位（0-15）。 */
    public static int effOf(BlockState state) {
        return state.getValue(LEVEL) * 2 + state.getValue(SUB);
    }

    private static BlockState withEff(BlockState state, int eff) {
        eff = Math.clamp(eff, 0, MAX_EFF);
        return state.setValue(LEVEL, eff / 2).setValue(SUB, eff % 2);
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
    // 结算：汲水 / 梯度传播 / 直落 / 灌田 / 邻接成型 / 旧档自愈
    // ============================================================

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // ① 旧档自愈：旧梘 watered=true → 迁移为满水并清除遗留标记
        if (state.getValue(WATERED)) {
            state = withEff(state, MAX_EFF).setValue(WATERED, false);
            level.setBlock(pos, state, 2);
        }

        int eff = effOf(state);
        int newEff = eff;
        boolean fillFluid = false;

        // ② 正下方汲水：水面 / 正在转的取水装置 → 灌满
        BlockPos below = pos.below();
        if (newEff < MAX_EFF
                && (level.getFluidState(below).is(FluidTags.WATER) || WaterDevices.isRunning(level.getBlockState(below)))) {
            newEff = MAX_EFF;
            fillFluid = true;
        }

        // ③ 水平梯度：邻梘水位比我高 ≥2 → 我涨 1 级（差保持 1，D12 压力梯度）
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockState nb = level.getBlockState(pos.relative(dir));
            if (nb.getBlock() instanceof JianBlock && effOf(nb) > newEff + 1 && newEff < MAX_EFF) {
                newEff++;
            }
        }

        // ④ 正下方直落：垂直方向不衰减（比水平梯度高一档）
        if (level.getBlockState(below).getBlock() instanceof JianBlock) {
            BlockState belowState = level.getBlockState(below);
            if (effOf(belowState) < newEff) {
                level.setBlock(below, withEff(belowState, newEff), 2);
                level.scheduleTick(below, this, INTERVAL);
            }
        }

        // ⑤ 灌田（漫灌）：以本段为源沿连体田洪水填充——
        //    需水量 = Σ(7-田水)+枯秆 1 点；供给 = 本段水位（1 级 = 1 点需水）；
        //    够就全灌，不够近处优先灌到水尽；一滴没浇出去就不扣水。
        if (newEff > 0 && random.nextInt(IRRIGATE_CHANCE) == 0) {
            newEff = floodIrrigate(level, pos, newEff);
        }

        // ⑥ 邻接成型：四侧是否有相邻梘段（multipart 模型按此开口）
        boolean cn = level.getBlockState(pos.north()).getBlock() instanceof JianBlock;
        boolean cs = level.getBlockState(pos.south()).getBlock() instanceof JianBlock;
        boolean ce = level.getBlockState(pos.east()).getBlock() instanceof JianBlock;
        boolean cw = level.getBlockState(pos.west()).getBlock() instanceof JianBlock;

        BlockState newState = state
                .setValue(CONNECT_NORTH, cn).setValue(CONNECT_SOUTH, cs)
                .setValue(CONNECT_EAST, ce).setValue(CONNECT_WEST, cw);
        newState = withEff(newState, newEff);
        if (newState != state) {
            level.setBlock(pos, newState, 2);
        }

        // ⑦ BE 对账：流体种类/液量与水位一致（渲染与 capability 的真相源）
        if (level.getBlockEntity(pos) instanceof JianBlockEntity jbe) {
            if (fillFluid && jbe.getFluid().isEmpty()) {
                jbe.setFluid(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1));
            }
            jbe.reconcile(effOf(newState));
        }

        level.scheduleTick(pos, this, INTERVAL);
    }

    /**
     * 漫灌：从槽段四邻出发，沿相邻稻秆/枯秆洪水填充整片连体田。
     *
     * <p>预算 = 传入的有效水位（1 级 = 1 点需水）；近处优先（BFS 天然按距离序），
     * 预算耗尽即停；一整轮下来一块田都没浇到（预算没花出去）则不扣水位。
     * 访问上限 256 格，防超大连体田卡结算。
     */
    private static int floodIrrigate(ServerLevel level, BlockPos pos, int budget) {
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
                if (deficit > 0) {
                    int give = Math.min(deficit, budget - spent);
                    level.setBlock(p, RiceStalkBlock.withMoisture(s, moisture + give), 2);
                    spent += give;
                }
            } else if (s.getBlock() == TiangongKaiwu.RICE_STALK_DRY.get()) {
                if (spent < budget) {
                    level.setBlock(p, RiceStalkBlockDry.reviveState(s), 3);
                    spent++;
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
        return spent > 0 ? budget - spent : budget; // 只实浇才扣水
    }

    // ============================================================
    // BE 与掉落
    // ============================================================

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
