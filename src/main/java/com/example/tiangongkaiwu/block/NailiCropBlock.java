package com.example.tiangongkaiwu.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 乃粒·旱地作物（批 5 杂粮三连：粟 / 黍 / 麻 / 菽）。
 *
 * <p>继承原版 {@link CropBlock} 全部机制：种在耕地、8 阶生长、骨粉催熟、
 * **不灌溉也能长（慢）**——白捡「北方无水利，赖天时」的机制底座。
 * 本类只加一件事：**雨天追一次生长**（书：秋雨连绵，旱谷争墒）。
 *
 * <p>掉落走战利品表（各作物一份）：谷粒即种子——收获多留一粒即可重种。
 * 贴图本批占位（原版 wheat 系），美术批统一出田园风 8 阶真图。
 */
public class NailiCropBlock extends CropBlock {

    public NailiCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        if (!level.isClientSide && this.getAge(state) < MAX_AGE && level.isRainingAt(pos.above())) {
            this.growCrops(level, pos, state);
        }
    }
}
