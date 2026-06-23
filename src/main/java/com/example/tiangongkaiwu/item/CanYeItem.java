package com.example.tiangongkaiwu.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class CanYeItem extends Item {

    public CanYeItem(Properties properties) {
        super(properties);
    }

    // 去掉 @Override，使用旧签名
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7" + "一张泛黄的古纸，上面写着看不懂的文字。"));
        tooltip.add(Component.literal("§7" + "或许翰墨台能帮你解读它。"));
        // 不调用 super，避免签名冲突
    }
}