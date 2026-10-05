package com.example.tiangongkaiwu.block.entity;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.block.PowerNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 筒（滑轮形态）方块实体：劲网三项指标缓存（Jade 显示用），
 * ticker 每 10 tick 各端自算、位置错峰——与 {@link ShaftBlockEntity} 同模式。
 */
public class TubeBlockEntity extends BlockEntity {

    /** 网络供给（Σ动力源）。 */
    public int supply = 0;
    /** 网络需求（Σ机器定额，含本筒）。 */
    public int demand = 0;
    /** 剩余劲占比 = 余/供（0..1；无供给时 0）。 */
    public float surplusRatio = 0.0F;

    public TubeBlockEntity(BlockPos pos, BlockState state) {
        super(TiangongKaiwu.TUBE_BE_TYPE.get(), pos, state);
    }

    /** 每 tick 由 ticker 调用；内部按 10 tick 步进 + 位置错峰，摊平开销。 */
    public void refreshStats() {
        Level level = this.level;
        if (level == null || level.getGameTime() % 10 != (this.worldPosition.hashCode() % 10 + 10) % 10) {
            return;
        }
        PowerNetwork.NetStats stats = PowerNetwork.stats(level, this.worldPosition);
        this.supply = stats.supply();
        this.demand = stats.demand();
        this.surplusRatio = stats.supply() > 0 ? stats.surplus() / (float) stats.supply() : 0.0F;
    }
}
