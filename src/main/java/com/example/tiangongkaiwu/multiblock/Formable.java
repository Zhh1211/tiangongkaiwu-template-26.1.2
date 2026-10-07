package com.example.tiangongkaiwu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 可成型机器核心（D15）：碓、桔槔、辘轳……凡走「墨斗弹线 → 拼装成型 → 机构方块运动件」
 * 路线的方块都实现它。墨斗与机构件只认这个接口，新机器零改动接入。
 */
public interface Formable {

    /** 机器模板 id（{@code data/tiangongkaiwu/structures/<id>.json}）。 */
    String templateId();

    /** 该方块态当前是否已成型。 */
    boolean isFormed(BlockState state);

    /** 返回置成 formed 状态后的方块态（不改世界）。 */
    BlockState withFormed(BlockState state, boolean formed);

    /** 拆线还原：机构件退回原方块、核心退回未成型态。 */
    void unformCore(ServerLevel level, BlockPos core);
}
