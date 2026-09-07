package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 水培稻的上部稻穗块（两段式结构中的上段，仅成熟期出现）。
 *
 * - 打穗（玩家破坏）→ 把下方秆的 AGE 重置为 {@link RiceStalkBlock#POST_HARVEST_AGE}，
 *   然后通过 {@code data/.../loot_table/blocks/rice_panicle.json} 掉落 2–3 粒稻谷（时运 +0~1）。
 *   秆可视地"缩回半截"再长穗。
 * - 邻居变化（下方的杆不是 rice_stalk）→ 自毁并掉落稻谷：覆盖秆毁/干秆化两种情形。
 *
 * 注：{@link #playerWillDestroy} 的 1.21.1 签名为 {@code (Level, BlockPos, BlockState, Player) → BlockState}，
 * 必须返回新的状态以替代被破坏的方块；这里返回默认值，让默认 destroy 流程跑 loot table。
 */
public class RicePanicleBlock extends Block {

    public RicePanicleBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(TiangongKaiwu.RICE_STALK.get());
    }

    /** 邻居变化（下方杆被打/枯）：自毁带掉落。 */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide && !level.getBlockState(pos.below()).is(TiangongKaiwu.RICE_STALK.get())) {
            level.destroyBlock(pos, true);
        }
    }

    /** 玩家打穗：先把下方秆 AGE 重置为收割后阶段，再走默认 destroy 流程（loot table 掉稻谷）。 */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
                                        net.minecraft.world.entity.player.Player player) {
        BlockState belowState = level.getBlockState(pos.below());
        if (belowState.is(TiangongKaiwu.RICE_STALK.get())) {
            level.setBlock(pos.below(),
                    belowState.setValue(RiceStalkBlock.AGE, RiceStalkBlock.POST_HARVEST_AGE), 2);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}