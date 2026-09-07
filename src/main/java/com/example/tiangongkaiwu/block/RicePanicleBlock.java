package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 水培稻的上部稻穗块（两段式结构中的上段，仅成熟期出现）。
 *
 * - 打穗（玩家破坏）→ 把下方秆的 AGE 重置为 {@link RiceStalkBlock#POST_HARVEST_AGE}，
 *   再直接代码弹出 2–3 粒稻谷（时运每级 +0~1，与 {@link #dropGrains} 一致）。秆可视地"缩回半截"再长穗。
 *   掉落走代码 {@link #playerDestroy} 而非 loot table——破坏路径确定、与资源/数据包加载解耦，
 *   也避免创造模式与玩家破坏流程差异导致"打掉不掉落"的困惑。
 * - 邻居变化（下方的秆不是 rice_stalk，如秆被挖/干秆化）→ 自毁并弹出稻谷（{@link #dropGrains}）。
 *
 * 注：{@link #playerWillDestroy} 的 1.21.1 签名为 {@code (Level, BlockPos, BlockState, Player) → BlockState}，
 * 必须返回新的状态以替代被破坏的方块；这里返回默认值，让默认 destroy 流程继续。
 */
public class RicePanicleBlock extends Block {

    public RicePanicleBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(TiangongKaiwu.RICE_STALK.get());
    }

    /** 邻居变化（下方秆被打/枯）：自毁 + 弹稻谷。 */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide && !level.getBlockState(pos.below()).is(TiangongKaiwu.RICE_STALK.get())) {
            level.destroyBlock(pos, false);
            dropGrains(level, pos, 0);
        }
    }

    /** 玩家打穗：先把下方秆 AGE 重置为收割后阶段，再弹稻谷。 */
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

    /** 玩家破坏收尾：直接弹 2–3 粒稻谷（时运每级 +0~1）。 */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (!level.isClientSide) {
            int fortune = tool.getEnchantments().getLevel(
                    level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.FORTUNE));
            dropGrains(level, pos, fortune);
        }
    }

    /** 弹出稻谷：基础 2–3 粒 + 时运每级额外 0~1 粒。 */
    private static void dropGrains(Level level, BlockPos pos, int fortune) {
        if (level.isClientSide) return;
        int base = 2 + level.random.nextInt(2);           // 2–3
        int extra = fortune > 0 ? level.random.nextInt(fortune + 1) : 0;
        popResource(level, pos, new ItemStack(TiangongKaiwu.RICE_GRAIN.get(), base + extra));
    }
}
