package com.example.tiangongkaiwu.client.gui;

import com.example.tiangongkaiwu.menu.HanmoTaiMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * 翰墨台译书界面（色块版，256×224）。
 *
 * 布局（相对 GUI 左上角，均可在此处微调）：
 *   - 左上：三个材料槽纵列（残页/墨/纸），槽坐标由 HanmoTaiMenu 决定（x=16, y=18/46/74）
 *   - 上中：残页纸区 —— 放入残页后点亮（破旧纸，后续显示文言题目）
 *   - 右上：墨水瓶 —— 放入墨后出现
 *   - 右侧中部：词块候选区（两列竖排 8 格）—— 有残页后点亮（后续放词块）
 *   - 下中：答题纸区 —— 放入纸后点亮（纸上格子用于摆放译词）
 *   - 底部：玩家背包 3×9 + 快捷栏（由父类按 Menu 槽坐标渲染）
 */
public class HanmoTaiScreen extends AbstractContainerScreen<HanmoTaiMenu> {

    // ============ 画布 ============
    private static final int GUI_W = 256;
    private static final int GUI_H = 224;

    // ============ 桌面配色（色块骨架，先不做纹理） ============
    private static final int WOOD_EDGE   = 0xFF3A2B1B; // 桌沿（外框）
    private static final int WOOD_BG     = 0xFF7B5B3E; // 桌面木色
    private static final int SLOT_BORDER = 0xFF2A1E10; // 槽框边
    private static final int SLOT_IN     = 0xFF8B7355; // 槽内底
    private static final int FRAG_ON     = 0xFFEBD9B4; // 残页纸（点亮/旧纸黄）
    private static final int FRAG_OFF    = 0xFFC4AF8B; // 残页纸（未放，暗淡）
    private static final int ANS_ON      = 0xFFF2E6C4; // 答题纸（点亮）
    private static final int ANS_OFF     = 0xFFD3C49F; // 答题纸（未放，暗淡）
    private static final int CELL_EDGE   = 0xFF7A6239; // 纸上格子边
    private static final int CELL_IN     = 0xFFCDB27E; // 纸上格子底
    private static final int INK_BODY    = 0xFF17130F; // 墨水瓶身（近黑）
    private static final int INK_HILITE  = 0xFF6E635A; // 瓶身高光
    private static final int INK_HOLD    = 0xFF3C2E1E; // 墨瓶空台（未放墨）
    private static final int TEXT_DARK   = 0xFF3B2C1A; // 深色文字（桌面/纸面上）

    // ============ 功能区布局（相对 GUI 左上角，坐标为示意骨架，可调） ============
    // 残页纸区（上中）
    private static final int FRAG_X = 70, FRAG_Y = 16, FRAG_W = 116, FRAG_H = 52;
    // 答题纸区（下中，玩家背包上方）
    private static final int ANS_X = 70, ANS_Y = 84, ANS_W = 116, ANS_H = 42;
    // 墨水瓶（右上角）—— y 与残页纸同高，靠右
    private static final int INK_X = 222, INK_Y = 14, INK_W = 24, INK_H = 34;
    // 词块候选区（右侧中部，墨水瓶下方）：两列 × 4 行 = 8 格
    private static final int BANK_X = 198, BANK_Y = 56, BANK_W = 46, BANK_H = 74;
    private static final int BANK_CELL_W = 18, BANK_CELL_H = 14;
    // 答案纸上示意格子：4 列 × 2 行（真实数量待接入题目后按词块数动态排）
    private static final int ANS_CELL_W = 23, ANS_CELL_H = 13;
    // 输入槽右侧的小标签起始 x（槽在 Menu 中 x=16）
    private static final int SLOT_LABEL_X = 40;

    public HanmoTaiScreen(HanmoTaiMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_W;
        this.imageHeight = GUI_H;
    }

    /**
     * 当前句的词块（正确顺序）。null 或空 = 尚未出题（未放残页 / 还没抽到题）。
     * 出题逻辑（#21）就绪后填充；候选词块区与答题格都只在该值非空时显示，
     * 数量 = 本句词块数，避免出现与题目无关的固定空框。
     */
    private List<String> currentTokens;

    /**
     * 1.21.1 的 AbstractContainerScreen.render() 已不再自动调用 renderTooltip
     * （只设置 hoveredSlot 与画槽位高亮），需子类在渲染末尾补一次，
     * 否则鼠标悬停物品无提示框。
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // ---------- 桌面：外沿深框 + 木色内面 ----------
        guiGraphics.fill(x, y, x + GUI_W, y + GUI_H, WOOD_EDGE);
        guiGraphics.fill(x + 3, y + 3, x + GUI_W - 3, y + GUI_H - 3, WOOD_BG);

        // 材料槽状态（客户端槽位内容由服务端同步，可直接读）
        boolean hasCanYe = !this.menu.slots.get(HanmoTaiMenu.SLOT_CAN_YE).getItem().isEmpty();
        boolean hasInk   = !this.menu.slots.get(HanmoTaiMenu.SLOT_INK).getItem().isEmpty();
        boolean hasPaper = !this.menu.slots.get(HanmoTaiMenu.SLOT_PAPER).getItem().isEmpty();

        // ---------- 残页纸区：放入残页后点亮（旧纸黄） ----------
        drawPaper(guiGraphics, x + FRAG_X, y + FRAG_Y, FRAG_W, FRAG_H,
                hasCanYe ? FRAG_ON : FRAG_OFF, hasCanYe);
        if (hasCanYe) {
            // 后续：在此区域中央显示第 N 句文言题目
        }

        // ---------- 答题纸区：放入纸后点亮；格子仅在出题后显示，数量 = 本句词块数 ----------
        drawPaper(guiGraphics, x + ANS_X, y + ANS_Y, ANS_W, ANS_H,
                hasPaper ? ANS_ON : ANS_OFF, hasPaper);
        if (hasPaper && this.currentTokens != null && !this.currentTokens.isEmpty()) {
            // 格子数 = 词块数（逐句作答，每格放一个词块）
            drawCells(guiGraphics, x, y, ANS_X, ANS_Y, ANS_W, ANS_H,
                    ANS_CELL_W, ANS_CELL_H, this.currentTokens.size());
        }

        // ---------- 墨水瓶（右上角）：放入墨后点亮为墨瓶 ----------
        if (hasInk) {
            int bx = x + INK_X, by = y + INK_Y;
            // 瓶身
            guiGraphics.fill(bx + 3, by + 6, bx + INK_W - 3, by + INK_H, INK_BODY);
            // 瓶肩高光（竖条）
            guiGraphics.fill(bx + 5, by + 8, bx + 7, by + INK_H - 2, INK_HILITE);
            // 瓶颈
            guiGraphics.fill(bx + 7, by + 3, bx + INK_W - 7, by + 8, INK_BODY);
            // 瓶盖/瓶口
            guiGraphics.fill(bx + 6, by, bx + INK_W - 6, by + 3, WOOD_EDGE);
        } else {
            // 空台：淡色小座，提示墨瓶将出现于此
            guiGraphics.fill(x + INK_X + 4, y + INK_Y + 24, x + INK_X + INK_W - 4, y + INK_Y + INK_H, INK_HOLD);
        }

        // ---------- 词块候选区（右侧中部）：仅在出题（有句子）后显示，数量 = 本句词块数 ----------
        if (hasCanYe && this.currentTokens != null && !this.currentTokens.isEmpty()) {
            int rows = (this.currentTokens.size() + 1) / 2;   // 两列排布
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < 2; col++) {
                    int cx = x + BANK_X + col * (BANK_CELL_W + 4);
                    int cy = y + BANK_Y + row * (BANK_CELL_H + 3);
                    guiGraphics.fill(cx, cy, cx + BANK_CELL_W, cy + BANK_CELL_H, CELL_IN);
                }
            }
            // 后续：在每个框内绘制打乱顺序的词块文字（#21）
        }

        // ---------- 所有槽（输入三槽 + 玩家背包）画槽底 ----------
        for (Slot slot : this.menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            guiGraphics.fill(sx, sy, sx + 18, sy + 18, SLOT_BORDER);
            guiGraphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT_IN);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 标题（方块名）
        guiGraphics.drawString(this.font, this.title, 8, 6, TEXT_DARK, false);
        // 输入槽标签（残页/墨/纸）
        drawSlotLabel(guiGraphics, HanmoTaiMenu.SLOT_CAN_YE, "gui.tiangongkaiwu.hanmo_slot_canye");
        drawSlotLabel(guiGraphics, HanmoTaiMenu.SLOT_INK, "gui.tiangongkaiwu.hanmo_slot_ink");
        drawSlotLabel(guiGraphics, HanmoTaiMenu.SLOT_PAPER, "gui.tiangongkaiwu.hanmo_slot_paper");
        // 玩家背包标题
        guiGraphics.drawString(this.font, Component.translatable("container.inventory"),
                16, 132, TEXT_DARK, false);
    }

    /** 在指定输入槽右侧画小标签（槽的 y 从 Menu 读取）。 */
    private void drawSlotLabel(GuiGraphics guiGraphics, int slotIndex, String langKey) {
        Slot slot = this.menu.slots.get(slotIndex);
        guiGraphics.drawString(this.font, Component.translatable(langKey),
                SLOT_LABEL_X, slot.y + 4, TEXT_DARK, false);
    }

    /** 画一张"纸"：纸面 + 底部右侧的 1px 阴影（制造"放在桌面上"的厚度）。 */
    private void drawPaper(GuiGraphics guiGraphics, int px, int py, int pw, int ph,
                           int fillColor, boolean lit) {
        guiGraphics.fill(px, py, px + pw, py + ph, fillColor);
        guiGraphics.fill(px + 2, py + ph, px + pw + 2, py + ph + 2, 0x40000000);
        guiGraphics.fill(px + pw, py + 2, px + pw + 2, py + ph, 0x40000000);
        if (lit) {
            // 点亮时左上角画一点高光，模拟纸的折痕/质感
            guiGraphics.fill(px + 2, py + 2, px + 10, py + 5, 0x30FFFFFF);
        }
    }

    /** 在纸面内部画等距格子：共 count 个（= 当前句词块数），按纸内面积自动分行居中。 */
    private void drawCells(GuiGraphics guiGraphics, int ox, int oy,
                           int areaX, int areaY, int areaW, int areaH,
                           int cellW, int cellH, int count) {
        int innerW = areaW - 16;
        int cols = Math.max(1, innerW / (cellW + 3));      // 每行最多几格（由纸宽决定）
        cols = Math.min(cols, count);
        int rows = Math.max(1, (count + cols - 1) / cols); // 需要几行
        int usedW = cols * cellW + (cols - 1) * 3;
        int startX = areaX + (areaW - usedW) / 2;          // 水平居中
        int startY = areaY + 5;                            // 纸内顶部往下一点
        int drawn = 0;
        outer:
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (drawn >= count) {
                    break outer;
                }
                int cx = startX + c * (cellW + 3);
                int cy = startY + r * (cellH + 3);
                guiGraphics.fill(ox + cx, oy + cy, ox + cx + cellW, oy + cy + cellH, CELL_EDGE);
                guiGraphics.fill(ox + cx + 1, oy + cy + 1, ox + cx + cellW - 1, oy + cy + cellH - 1, CELL_IN);
                drawn++;
            }
        }
    }
}
