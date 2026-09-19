package com.example.tiangongkaiwu.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 水利装置的统一入口。传动轴与梘都通过这里问两件事：
 * 「这台装置在转吗、能发出多少动力」——不必逐个认识筒车／牛车／踏车／拔车。
 *
 * <p>将来再加水车家族（龙骨车其它驱动、桔槔、辘轳、风帆车）时，只在这里补一行分支，
 * 下游的动力网与引水槽都不用改。
 */
public final class WaterDevices {

    private WaterDevices() {
    }

    /** 本格能发出多少动力（不转就是 0）。 */
    public static int emittedPower(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof TongCheBlock) {
            return TongCheBlock.emittedPower(state);
        }
        if (block instanceof DragonBoneCarBlock car) {
            return car.emittedPower(state);
        }
        return 0;
    }

    /** 这台装置在转吗（转着的车／轮可以把水送给梘）。 */
    public static boolean isRunning(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof TongCheBlock) {
            return TongCheBlock.isRunning(state);
        }
        if (block instanceof DragonBoneCarBlock car) {
            return car.isRunning(state);
        }
        return false;
    }
}
