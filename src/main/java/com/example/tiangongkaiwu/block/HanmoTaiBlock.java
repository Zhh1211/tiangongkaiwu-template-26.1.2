package com.example.tiangongkaiwu.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 翰墨台方块
 * 用于翻译残页的核心工作台
 */
public class HanmoTaiBlock extends Block {

    public HanmoTaiBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            // TODO: 后续实现打开 GUI
            player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                    "§6翰墨台正在准备中... 功能开发中。"
                )
            );
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }

    // TODO: 后续添加 BlockEntity 实现 GUI 和数据存储
}