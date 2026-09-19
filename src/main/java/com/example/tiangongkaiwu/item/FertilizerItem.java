package com.example.tiangongkaiwu.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 对症肥料（稻宜·粪田）。
 *
 * <p>书里最要紧的一句是「**用错土性不宜也**」，所以三样各有各的主场：
 * 粪肥通用（人畜穢遺，普天之所同也）；骨灰／石灰只宜**冷浆土**（黏土田）；
 * 烧土（潜行 + 薪柴）只宜**坚紧土**（砂砾／粗泥田）。
 *
 * <p>生效判定不在这里，而在 {@link com.example.tiangongkaiwu.block.RiceStalkBlock} 的
 * {@code useItemOn} —— 只有那一刻才知道田底下铺的是什么土。
 */
public class FertilizerItem extends Item {

    private final String tipKey;
    private final String tipKey2;

    public FertilizerItem(Properties properties, String id) {
        super(properties);
        this.tipKey = "item.tiangongkaiwu." + id + ".tip";
        this.tipKey2 = "item.tiangongkaiwu." + id + ".tip2";
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(this.tipKey).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(this.tipKey2).withStyle(ChatFormatting.DARK_GRAY));
    }
}
