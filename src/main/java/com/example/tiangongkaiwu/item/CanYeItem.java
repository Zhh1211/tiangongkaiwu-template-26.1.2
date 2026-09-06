package com.example.tiangongkaiwu.item;

import com.example.tiangongkaiwu.TiangongKaiwu;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 《天工》残页 —— 单物品 + 数据组件走完整生命周期（2026-09-06 用户拍板，无独立“译后残页”物品）。
 *
 * 状态机（组件 {@link ResidualData}）：
 *   无组件            = 泛黄空页（创造栏占位/调试用），不参与玩法
 *   translated=false  = 待译：放进翰墨台抽「该条目题池」的题；右键仅提示去翰墨台
 *   translated=true   = 已译：右键 → 解锁条目（grant 隐藏 advancement，Modonomicon ≤5s 自动开锁）
 *                       + 发放烙在组件上的成绩经验；条目早已解锁则只给经验。用完即毁（创造除外）。
 */
public class CanYeItem extends Item {

    public CanYeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ResidualData data = stack.get(TiangongKaiwu.RESIDUAL.get());
        if (data == null) {
            tooltip.add(Component.translatable("item.tiangongkaiwu.can_ye.tooltip1").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.tiangongkaiwu.can_ye.tooltip2").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!data.entry().isBlank()) {
            tooltip.add(Component.translatable("item.tiangongkaiwu.can_ye.unlock_prefix",
                            entryName(data)).withStyle(ChatFormatting.DARK_GREEN));
        }
        tooltip.add(Component.translatable(data.translated()
                        ? "item.tiangongkaiwu.can_ye.status.done"
                        : "item.tiangongkaiwu.can_ye.status.undone")
                .withStyle(ChatFormatting.GRAY));
        if (data.translated()) {
            tooltip.add(Component.translatable("item.tiangongkaiwu.can_ye.status.xp", data.xp())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * 右键使用（服务端权威）：
     * 无目标/待译 → 只发提示；已译 → 解锁条目 + 发经验 + 用完即毁。
     * 注意：1.21.1 物品 use 返回 InteractionResultHolder&lt;ItemStack&gt;（方块才是 InteractionResult）。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack); // 实际逻辑在服务端执行
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }
        ResidualData data = stack.get(TiangongKaiwu.RESIDUAL.get());

        if (data == null || data.entry().isBlank()) {
            serverPlayer.sendSystemMessage(Component.translatable("item.tiangongkaiwu.can_ye.use.untargeted"));
            return InteractionResultHolder.success(stack);
        }
        if (!data.translated()) {
            serverPlayer.sendSystemMessage(Component.translatable("item.tiangongkaiwu.can_ye.use.undone"));
            return InteractionResultHolder.success(stack);
        }

        // ===== 已译：解锁条目（若有锁）+ 发放经验 + 用完即毁 =====
        Component name = entryName(data);
        int xp = Math.max(0, data.xp());
        boolean newlyUnlocked = grantUnlockAdvancement(serverPlayer, data.tail());
        if (xp > 0) {
            serverPlayer.giveExperiencePoints(xp);
        }
        if (newlyUnlocked) {
            serverPlayer.sendSystemMessage(Component.translatable(
                    "item.tiangongkaiwu.can_ye.use.unlocked", name, xp));
        } else {
            serverPlayer.sendSystemMessage(Component.translatable(
                    "item.tiangongkaiwu.can_ye.use.already", xp));
        }
        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.success(stack.isEmpty() ? ItemStack.EMPTY : stack);
    }

    /**
     * 把条目尾段映射到隐藏解锁 advancement（tiangongkaiwu:unlock/<tail>）并 grant。
     * 返回是否真的发生了“首次解锁”；无对应锁（如总论引导篇）或早已解锁返回 false（只发经验）。
     */
    private static boolean grantUnlockAdvancement(ServerPlayer player, String tail) {
        ResourceLocation advId = ResourceLocation.fromNamespaceAndPath(TiangongKaiwu.MODID, "unlock/" + tail);
        AdvancementHolder holder = player.server.getAdvancements().get(advId);
        if (holder == null) {
            return false;
        }
        if (player.getAdvancements().getOrStartProgress(holder).isDone()) {
            return false;
        }
        player.getAdvancements().award(holder, "unlock");
        return true;
    }

    /** 条目显示名：条目尾段拼翻译键 entry.tiangongkaiwu.&lt;tail&gt;.name（教程书数据已提供）。 */
    private static Component entryName(ResidualData data) {
        return Component.translatable("entry.tiangongkaiwu." + data.tail() + ".name");
    }
}
