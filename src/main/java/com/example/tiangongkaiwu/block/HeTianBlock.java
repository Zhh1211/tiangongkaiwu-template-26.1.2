package com.example.tiangongkaiwu.block;

import net.minecraft.world.level.block.Block;

/**
 * 涸田（批 4 排涝造田，书：「去澤水以便栽種」）。
 *
 * <p>筒·滑轮形态把小水体抽干后，抽干格若落在可作田的土上，就**沉降一格涸田**——
 * 排涝的奖励：这片地从此能直接插秧（旱插，田水从潮润的低档起步，之后照常蒸发/回充）。
 * 挖掉掉自身，可以搬去别处垫田底。
 *
 * <p>插秧规则完全复用 {@link RiceStalkBlock#isPaddySoil}（涸田本身就是合格田底），
 * 新增一条"旱插"入口见 {@code RiceGrainItem}。
 */
public class HeTianBlock extends Block {

    public HeTianBlock(Properties properties) {
        super(properties);
    }
}
