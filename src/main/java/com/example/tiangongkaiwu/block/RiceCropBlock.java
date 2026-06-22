package com.example.tiangongkaiwu.block;

import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.ItemLike;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;   // 新增导入
import net.minecraft.world.level.block.Blocks;

import com.example.tiangongkaiwu.TiangongKaiwu;

public class RiceCropBlock extends CropBlock {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;

    public RiceCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return TiangongKaiwu.RICE_SEED.get();
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { // 改为 LevelReader
        BlockState below = level.getBlockState(pos.below());
        return below.is(Blocks.FARMLAND);
    }
}