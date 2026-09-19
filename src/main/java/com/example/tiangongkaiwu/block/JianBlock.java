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

/**
 * 梘（引水槽）：书里「一一傾於梘內，流入畝中」那根水槽。
 *
 * <p>做法是"抽象输水"——槽里不放假的水方块（原版水自己会流、会漏，反而难用），
 * 只记两件事：① 我这一段有没有水；② 有水时替挨着的稻田补水。
 *
 * <p>判定很朴素：看**上游那一格**（朝向的反面）——那里是带水的梘、是正在转的筒车，
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

    /** 上游（朝向的反面）那一格有水／是带水的槽／是正在转的筒车 → 我这一段就有水。 */
    private static boolean isFed(ServerLevel level, BlockState state, BlockPos pos) {
        BlockPos upstream = pos.relative(state.getValue(FACING).getOpposite());
        BlockState up = level.getBlockState(upstream);
        if (up.getBlock() instanceof JianBlock) {
            return up.getValue(WATERED);
        }
        if (up.getBlock() instanceof TongCheBlock) {
            return TongCheBlock.isRunning(up);
        }
        return level.getFluidState(upstream).is(FluidTags.WATER);
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
}
