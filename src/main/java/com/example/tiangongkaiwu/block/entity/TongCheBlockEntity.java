package com.example.tiangongkaiwu.block.entity;

import com.example.tiangongkaiwu.TiangongKaiwu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 筒车的方块实体：只承载两个客户端视觉字段（都不存盘）。
 *
 * <p>转角本体由世界时间同源计算（全服同步，2026-10-03 拍板）；
 * 这里存的是「停转瞬间冻在哪个角度」——车停了（断水/栓木）轮子就该
 * 停在当下的位置，而不是跳回 0 度。引入 BE 是因为 3D 大轮的旋转
 * 要靠 {@code TongCheRenderer}（BER）做。
 */
public class TongCheBlockEntity extends BlockEntity {

    /** 停转时冻结的角度（度）。仅客户端使用。 */
    public float frozenAngle;
    /** 上一帧是否在转（检测「转→停」的瞬间，抓拍冻结角）。仅客户端使用。 */
    public boolean lastActive;

    public TongCheBlockEntity(BlockPos pos, BlockState state) {
        super(TiangongKaiwu.TONG_CHE_BE_TYPE.get(), pos, state);
    }
}
