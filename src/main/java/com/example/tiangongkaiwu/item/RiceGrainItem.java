package com.example.tiangongkaiwu.item;

import com.example.tiangongkaiwu.TiangongKaiwu;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * 稻谷：单一物品兼种子与脱粒原料（不可食）。
 *
 * 种植：手持此物对**静止水源**右键 → 若底部是泥土系/泥（{@link BlockTags#DIRT} + {@link Blocks#MUD}）
 *       且正上方为空气，则把水源格替换为含水状态下的 {@link com.example.tiangongkaiwu.block.RiceStalkBlock}（AGE 0），
 *       消耗 1 粒。非可种位置直接 PASS，不消耗。
 *
 * （"门控=锁源头"：v1 不写代码硬锁种植；未解锁条目的玩家拿不到种子——种子只能靠
 *   {@code unlock/naili_do} 成就奖励掉落表送 4 粒入手。）
 */
public class RiceGrainItem extends Item {

    public RiceGrainItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();

        // 必须点击静止水源格
        if (!state.is(Blocks.WATER) || !level.getFluidState(pos).isSource()) {
            return InteractionResult.PASS;
        }
        // 底部须为泥土系/泥（造田要求）
        BlockState below = level.getBlockState(pos.below());
        boolean soilOk = below.is(BlockTags.DIRT) || below.getBlock() == Blocks.MUD;
        if (!soilOk) return InteractionResult.FAIL;
        // 正上方须空气（不种在水面被遮挡处）
        if (!level.getBlockState(pos.above()).isAir()) return InteractionResult.FAIL;

        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        level.setBlock(pos, TiangongKaiwu.RICE_STALK.get().defaultBlockState(), 3);
        if (player != null && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(player, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
        return InteractionResult.sidedSuccess(false);
    }
}