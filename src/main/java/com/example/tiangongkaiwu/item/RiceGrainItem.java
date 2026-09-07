package com.example.tiangongkaiwu.item;

import com.example.tiangongkaiwu.TiangongKaiwu;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 稻谷：单一物品兼种子与脱粒原料（不可食）。
 *
 * 种植：手持此物对**静止水源**右键 → 若底部是泥土系/泥（{@link BlockTags#DIRT} + {@link Blocks#MUD}）
 *       且正上方为空气，则把水源格替换为含水状态下的 {@link com.example.tiangongkaiwu.block.RiceStalkBlock}（AGE 0），
 *       消耗 1 粒。
 *
 * <p>命中处理：普通右键的方块射线默认「穿透流体」（Fluid.NONE），瞄准水面时命中的往往是
 * 水底/对岸固体而非水格，直接拿 {@code UseOnContext.getClickedPos()} 判断永远失败。
 * 因此这里仿照空桶舀水，用 {@link Item#getPlayerPOVHitResult 含流体的准星射线}
 * （{@link ClipContext.Fluid#SOURCE_ONLY}）定位「玩家准星所指的水源格」，命中该格才可种。
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
        // 玩家点击水格旁的固体（最常见：水下的田底）时，useOn 拿到的 hit 是那个固体，
        // 但 use() 兜底并不会触发——所以必须在这里就用含流体准星射线找水源格。
        return plantIntoWater(ctx.getLevel(), ctx.getPlayer(), ctx.getItemInHand());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // 当准星直接落在水面（射线不触任何固体）时走 use() 入口，同样用含流体射线种植。
        ItemStack stack = player.getItemInHand(hand);
        return new InteractionResultHolder<>(plantIntoWater(level, player, stack), stack);
    }

    /** 核心：对准星所指的水源格插秧（共用入口，天然避免重复消耗）。 */
    private InteractionResult plantIntoWater(Level level, Player player, ItemStack stack) {
        if (player == null) return InteractionResult.PASS;

        // 仿照空桶：射线只认"静止水源"，拿到玩家真正指着的那格水
        BlockHitResult hit = Item.getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
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
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(player, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
        return InteractionResult.sidedSuccess(false);
    }
}
