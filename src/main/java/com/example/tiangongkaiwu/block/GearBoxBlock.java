package com.example.tiangongkaiwu.block;

import net.minecraft.world.level.block.Block;

/**
 * 十字齿轮箱（D17 格物·合轮的奖品，照搬机械动力的 Gearbox 思路）：
 * 劲网的**全通节点**——劲从任意方向进、其余五向皆可出（兼直通与变向）。
 *
 * <p>普通版（横向）与竖版（`gear_box_vertical`，合成转换互换）仅外观有别，劲网行为一致。
 * 牙轮仍是 90° 变向的廉价方案；齿轮箱是格物致知后的省操作升级件。
 *
 * <p>劲网集成在 {@link PowerNetwork}（instanceof 全通分支）。
 */
public class GearBoxBlock extends Block {

    public GearBoxBlock(Properties properties) {
        super(properties);
    }
}
