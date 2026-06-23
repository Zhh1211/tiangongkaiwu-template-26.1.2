package com.example.tiangongkaiwu.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class HanmoTaiBlock extends Block {

    public HanmoTaiBlock(Properties properties) {
        super(properties);
    }

    // 去掉 @Override，因为签名可能不完全匹配父类
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide()) {  // ← 改为方法调用
            player.sendSystemMessage(Component.literal("§6翰墨台正在准备中... 功能开发中。"));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }
}