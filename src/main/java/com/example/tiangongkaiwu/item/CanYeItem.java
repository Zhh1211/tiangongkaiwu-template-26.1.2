package com.example.tiangongkaiwu.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 《天工》残页物品
 * 右键可打开翰墨台进行翻译（暂未实现）
 */
public class CanYeItem extends Item {

    public CanYeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7" + "一张泛黄的古纸，上面写着看不懂的文字。"));
        tooltip.add(Component.literal("§7" + "或许翰墨台能帮你解读它。"));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    // TODO: 后续实现右键打开翰墨台GUI
}