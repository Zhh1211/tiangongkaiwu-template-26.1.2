package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 梘（引水槽）：书里「一一傾於梘內，流入畝中」那根水槽。
 *
 * <p>做法是"抽象输水"——槽里不放假的水方块（原版水自己会流、会漏，反而难用），
 * 只记两件事：① 我这一段有没有水；② 有水时替挨着的稻田补水。
 *
 * <p>判定很朴素：看**上游那一格**（朝向的反面）——那里是带水的梘、是正在转的车／轮，
 * 或者干脆就是一格水，我这一段就有水。每格只看上游一格，所以不会递归、也不会炸。
 * 好处是能把水**抬过地形、引到远处**，这是挖沟做不到的。
 *
 * <p>顺带：渠水到田，枯秆也会自己活过来（对齐书里"天泽"与"人力挽水以济"）。
 */
public class JianBlock extends Block {

    /** 水流方向（放置时 = 玩家面朝的方向）。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 这一段槽里有没有水。 */
    public static final BooleanProperty WATERED = BooleanProperty.create("watered");

    /** 每 10 tick 结算一次上游来水。 */
    public static final int INTERVAL = 10;
    /** 邻居变化后快速重算。 */
    public static final int REACT_DELAY = 2;
    /** 补水节奏：每次结算有 1/4 概率给田补水 → 平均 40 tick（2 秒）补 1 格。 */
    private static final int IRRIGATE_CHANCE = 4;

    public JianBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
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

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean fed = isFed(level, state, pos);
        if (fed != state.getValue(WATERED)) {
            level.setBlock(pos, state.setValue(WATERED, fed), 2);
        }
        if (fed && random.nextInt(IRRIGATE_CHANCE) == 0) {
            irrigate(level, pos);
        }
        level.scheduleTick(pos, this, INTERVAL);
    }

    /**
     * 这一段有没有水：**先看上游一格（朝向的反面），再看四个水平邻格与正下方**。
     *
     * <p>原实现只看上游一格，玩家把槽贴着水/筒车放却"接不上"（2026-09-20 实测反馈：
     * 「放筒车旁边没有水」）——因为朝向得刚好对着水源才算。放宽后贴哪边都行，
     * 但仍**不看下游**，避免自反馈成环。
     */
    private static boolean isFed(ServerLevel level, BlockState state, BlockPos pos) {
        if (fedBy(level, pos.relative(state.getValue(FACING).getOpposite()))) {
            return true;      // 上游优先
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (fedBy(level, pos.relative(dir))) {
                return true;  // 两侧也认（贴哪边都能接上）
            }
        }
        return fedBy(level, pos.below());
    }

    /** 某一格能不能供水（带水的梘 / 正在转的装置 / 一格水）。 */
    private static boolean fedBy(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.getBlock() instanceof JianBlock) {
            return s.getValue(WATERED);
        }
        return WaterDevices.isRunning(s) || level.getFluidState(p).is(FluidTags.WATER);
    }

    /** 给紧挨着的稻田补水（四邻 + 正下方）。 */
    private static void irrigate(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            waterCell(level, pos.relative(dir));
        }
        waterCell(level, pos.below());
    }

    private static void waterCell(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() == TiangongKaiwu.RICE_STALK.get()) {
            int moisture = state.getValue(RiceStalkBlock.MOISTURE);
            if (moisture < RiceStalkBlock.MAX_MOISTURE) {
                level.setBlock(pos, RiceStalkBlock.withMoisture(state, moisture + 1), 2);
            }
        } else if (state.getBlock() == TiangongKaiwu.RICE_STALK_DRY.get()) {
            level.setBlock(pos, RiceStalkBlockDry.reviveState(state), 3);
        }
    }

    /** 挖掉掉自己。 */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        popResource(level, pos, new ItemStack(TiangongKaiwu.JIAN_ITEM.get()));
    }
    // ====== 碰撞箱（2026-09-20 补：原先 noCollission 导致玩家能穿过整套装置，
    // 踏车更是站不上去、永远转不起来）======
    /** 梘（浅槽：低位碰撞，能踩过去） */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                       CollisionContext context) {
        return Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D);
    }
}
