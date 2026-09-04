package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.menu.HanmoTaiMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class HanmoTaiBlock extends Block {

    public HanmoTaiBlock(Properties properties) {
        super(properties);
    }

    // 暂时去掉 @Override，直到确认父类签名
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> new HanmoTaiMenu(containerId, inventory),
                Component.literal("翰墨台")
            ));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }
}