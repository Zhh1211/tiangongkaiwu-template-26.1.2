package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.block.entity.HanmoTaiBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

public class HanmoTaiBlock extends Block implements EntityBlock {

    public HanmoTaiBlock(Properties properties) {
        super(properties);
    }

    // ============================================================
    // 方块实体：翰墨台是容器方块，材料存放在方块内
    // ============================================================
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HanmoTaiBlockEntity(pos, state);
    }

    /**
     * 方块被破坏/替换时，把方块内尚未取走的材料掉落出来。
     * 方块本体仍按战利品表正常掉落。
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof HanmoTaiBlockEntity hanmo) {
                Containers.dropContents(level, pos, hanmo);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // 重要：NeoForge 1.21.1 中 Block.use(...) 已不存在，
    // 右键交互被拆成 useWithoutItem（空手）与 useItemOn（手持物品）两个方法。
    // 本轮只实现空手右键打开界面；手持物品右键暂走原版默认（正常放置/使用物品）。
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof HanmoTaiBlockEntity hanmo) {
                player.openMenu(hanmo);
            }
        }
        return InteractionResult.CONSUME;
    }
}
