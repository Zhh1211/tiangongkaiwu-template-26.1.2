package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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

    /** 这台装置在转吗（转着的车／轮／井具可以把水送给梘）。 */
    public static boolean isRunning(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof TongCheBlock) {
            return TongCheBlock.isRunning(state);
        }
        if (block instanceof DragonBoneCarBlock car) {
            return car.isRunning(state);
        }
        // 桔槔／辘轳也算"在提水"，所以它们可以喂梘。
        // 注意：风帆车**故意不在**这里——它是**排水**的（书：去水非取水也），不能倒着给梘送水。
        if (block instanceof WellLiftBlock lift) {
            return lift.isRunning(state);
        }
        return false;
    }

    // ==================== 给稻田补水（共用小工具） ====================

    /** 给一格补水：是稻田就 +1 田水，是枯秆就复活。 */
    public static void waterCell(ServerLevel level, BlockPos pos) {
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

    /** 给紧挨着这一格的田补水（四邻 + 正下方）。 */
    public static void irrigateAround(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            waterCell(level, pos.relative(dir));
        }
        waterCell(level, pos.below());
    }
}
