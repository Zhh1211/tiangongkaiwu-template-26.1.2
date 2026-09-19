package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * 枯秆方块：稻田断水后的状态（稻灾·水利 A 批）。
 *
 * - 触发：田水耗尽（MOISTURE 归零）自动转入，或玩家用空桶把水舀到见底。
 * - 不生长、不抽穗；仅保留原秆的 AGE（断水时的生长阶段），复活时原样还回。
 * - **不会立刻死**：随机 tick 以极小概率枯死（约合书里"旬日失水则死"的十天量级），
 *   留出挑水抢救的时间窗。
 * - 复活：① 玩家手持水桶右键（{@link #placeLiquid}）；② 天降雨水（随机 tick 检测降雨）。
 *   两者都复活为同 AGE 的浸水稻秆，并把田水灌满。
 *
 * 设计依据见 docs/乃粒稻作玩法设计.md（水利 A 批）与 docs/天工开物·乃粒原文.md「水利／稻」节。
 */
public class RiceStalkBlockDry extends Block implements SimpleWaterloggedBlock {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    /**
     * 枯死概率（每随机 tick）≈ 1/176。默认 randomTickSpeed=3 时约合 10 个游戏日，
     * 对应书里「凡稻旬日失水則死期至」。
     */
    private static final float DEATH_CHANCE = 0.0057f;

    public RiceStalkBlockDry(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AGE, 0)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, WATERLOGGED);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean canPlaceLiquid(Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return fluid == Fluids.WATER;
    }

    /** 水桶倒在枯秆上：复活为同 AGE、满田水的浸水稻秆（把断水前的生长阶段还回去）。 */
    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        if (fluid.getType() != Fluids.WATER) {
            return false;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, reviveState(state), 3);
        }
        return true;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return RiceStalkBlock.isPaddySoil(level.getBlockState(pos.below()));
    }

    /**
     * 随机 tick：久旱逢雨 → 复活；否则以极小概率枯死（约"旬日"）。
     * 书曰「天澤不降，則人力挽水以濟」——天一旦降雨，田自己就活了。
     */
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.isRainingAt(pos.above())) {
            level.setBlock(pos, reviveState(state), 3);
            return;
        }
        if (random.nextFloat() < DEATH_CHANCE) {
            level.destroyBlock(pos, false);
        }
    }

    /** 复活态：同 AGE 的浸水稻秆 + 田水灌满。 */
    public static BlockState reviveState(BlockState dryState) {
        return TiangongKaiwu.RICE_STALK.get().defaultBlockState()
                .setValue(RiceStalkBlock.AGE, dryState.getValue(AGE))
                .setValue(RiceStalkBlock.WATERLOGGED, true)
                .setValue(RiceStalkBlock.MOISTURE, RiceStalkBlock.MAX_MOISTURE);
    }
}
