package com.example.tiangongkaiwu.block.entity;

import com.example.tiangongkaiwu.TiangongKaiwu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 筒车的方块实体：目前只承载一个客户端视觉字段——轮子的累计转角。
 *
 * <p>不存任何数据（转角是纯视觉，读档归零无所谓）；引入它是因为
 * 3D 大轮的旋转要靠 {@code TongCheRenderer}（BER）做，BER 需要 BE 挂在方块上。
 */
public class TongCheBlockEntity extends BlockEntity {

    /** 客户端累计转角（度）。仅在 ACTIVE 时由渲染器推进。 */
    public float wheelAngle;

    public TongCheBlockEntity(BlockPos pos, BlockState state) {
        super(TiangongKaiwu.TONG_CHE_BE_TYPE.get(), pos, state);
    }
}
