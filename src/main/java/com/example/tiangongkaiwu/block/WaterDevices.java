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

    /**
     * 这台井具在提水吗（桔槔/辘轳的本职：井上提水可以直接喂梘，不经筒）。
     *
     * <p>D16 车系拆分（2026-10-05）：**车系（筒车/牛车/踏车/拔车）不再直接喂梘**——
     * 车只产劲，流体做功归筒（泵形态/滑轮形态）。风帆车依旧**故意不在**这里
     * （书：去水非取水也；排涝走筒·滑轮形态）。
     */
    public static boolean isRunning(BlockState state) {
        if (state.getBlock() instanceof WellLiftBlock lift) {
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
