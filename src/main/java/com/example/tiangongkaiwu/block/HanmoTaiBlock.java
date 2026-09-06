package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.menu.HanmoTaiMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class HanmoTaiBlock extends Block {

    public HanmoTaiBlock(Properties properties) {
        super(properties);
    }

    // 重要：NeoForge 1.21.1 中 Block.use(...) 已不存在，
    // 右键交互被拆成 useWithoutItem（空手）与 useItemOn（手持物品）两个方法。
    // 本轮只实现空手右键打开界面；手持物品右键暂走原版默认（正常放置/使用物品）。
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // 把方块位置传给菜单，用于 stillValid 判定（被推走/传送走时界面自动关闭）
        player.openMenu(new SimpleMenuProvider(
            (containerId, inventory, p) ->
                new HanmoTaiMenu(containerId, inventory, ContainerLevelAccess.create(level, pos)),
            Component.literal("翰墨台")
        ));
        return InteractionResult.CONSUME;
    }
}
