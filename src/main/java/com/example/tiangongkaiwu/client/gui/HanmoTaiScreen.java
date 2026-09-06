package com.example.tiangongkaiwu.client.gui;

import com.example.tiangongkaiwu.menu.HanmoTaiMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class HanmoTaiScreen extends AbstractContainerScreen<HanmoTaiMenu> {

    public HanmoTaiScreen(HanmoTaiMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // 占位背景：后续换成翰墨台贴图（assets/tiangongkaiwu/textures/gui/hanmo_tai.png）
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF8B7355);
        guiGraphics.fill(x + 4, y + 4, x + this.imageWidth - 4, y + this.imageHeight - 4, 0xFFE8DFC8);

        // 输入槽区域底衬，提示此处放残页
        guiGraphics.fill(x + 61, y + 34, x + 115, y + 53, 0xFFCDBCA0);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        guiGraphics.drawString(this.font, "背包", this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }
}
