package com.example.tiangongkaiwu.block.entity;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.block.PowerNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 传动杆方块实体：BER 旋转挂载点 + 网络三项指标缓存。
 *
 * <p>指标（供给/需求/剩余）由 ticker 每 10 tick 各端自算一次（读的是本端世界，
 * 双端一致所以免同步）——显示层（Jade/红线）只读缓存，不触发扫描。
 * 将来上 BE 网络换增量记账时，只动这里（Create KineticNetwork 模式）。
 */
public class ShaftBlockEntity extends BlockEntity {

    /** 网络供给（Σ动力源）。 */
    public int supply = 0;
    /** 网络需求（Σ机器定额）。 */
    public int demand = 0;
    /** 剩余劲占比 = 余/供（0..1，红线用；无供给时 0）。 */
    public float surplusRatio = 0.0F;

    public ShaftBlockEntity(BlockPos pos, BlockState state) {
        super(TiangongKaiwu.SHAFT_BE_TYPE.get(), pos, state);
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
