package com.example.tiangongkaiwu.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class CanYeItem extends Item {

    public CanYeItem(Properties properties) {
        super(properties);
    }

    /**
     * 1.21.1 的签名为 (ItemStack, Item.TooltipContext, List, TooltipFlag)。
     * 旧写法用 Level 作第二参且不加 @Override，会编译通过但永不被调用（静默失效）。
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tiangongkaiwu.can_ye.tooltip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.tiangongkaiwu.can_ye.tooltip2").withStyle(ChatFormatting.GRAY));
    }
}
