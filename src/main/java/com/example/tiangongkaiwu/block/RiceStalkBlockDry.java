package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * 枯秆方块：演示版稻灾的状态——{@link RiceStalkBlock} 被玩家用空桶舀水后转入此态。
 *
 * - 不生长、不抽穗（仅含"是否含水"属性；默认 waterlogged=false 表现为干燥秆）。
 * - 随机 tick 约 25% 自毁（无掉落），体现"旬日失水则死期至"。
 * - 玩家手持水桶右键 → {@link #placeLiquid} 把它复活为年轻的 {@link RiceStalkBlock}（AGE 0，含水）。
 */
public class RiceStalkBlockDry extends Block implements SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public RiceStalkBlockDry(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean canPlaceLiquid(Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return fluid == Fluids.WATER;
    }

    /** 水桶倒在枯秆上：复活为幼龄浸水稻秆（AGE 0）。返回 boolean 表示是否成功占位。 */
    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        if (fluid.getType() == Fluids.WATER) {
            BlockState newState = TiangongKaiwu.RICE_STALK.get().defaultBlockState();
            return level.setBlock(pos, newState, 3);
        }
        return false;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(BlockTags.DIRT) || below.getBlock() == net.minecraft.world.level.block.Blocks.MUD;
    }

    /** 约 25% 概率自毁（无掉落）→ "失水日久枯死"。 */
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            level.destroyBlock(pos, false);
        }
    }
}