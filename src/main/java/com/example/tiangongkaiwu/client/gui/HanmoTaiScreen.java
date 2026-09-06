package com.example.tiangongkaiwu.client.gui;

import com.example.tiangongkaiwu.hanmo.Puzzle;
import com.example.tiangongkaiwu.hanmo.PuzzleRegistry;
import com.example.tiangongkaiwu.menu.HanmoTaiMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 翰墨台译书界面（色块版，288×224）。
 *
 * 布局（相对 GUI 左上角，均可在此处微调）：
 *   - 左上：三个材料槽纵列（残页/墨/纸），槽坐标由 HanmoTaiMenu 决定（x=16, y=18/46/74）
 *   - 左中上：残页纸（题面）—— 放入残页后点亮并抽题，显示文言句子
 *   - 右上：墨水瓶 —— 放入墨后出现
 *   - 右列：词块候选区 —— 出题后按本句词块数显示，两列排布，词块乱序
 *   - 左中下：答题纸 —— 放入纸后点亮，格子数 = 本句词块数
 *   - 底部：玩家背包 3×9 + 快捷栏（水平居中，由父类按 Menu 槽坐标渲染）
 *
 * 出题流程（显示层，拖放/判定在后续任务）：放入残页 → 从客户端题库随机抽一题，
 * 显示第一句文言与乱序词块；词块与格子数量都由当前句词块数决定；
 * 取出残页即清空状态。
 */
public class HanmoTaiScreen extends AbstractContainerScreen<HanmoTaiMenu> {

    // ============ 画布 ============
    private static final int GUI_W = 288;
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
    private static final int CHIP_TEXT   = 0xFF241A0E; // 词块文字（近黑）

    // ============ 功能区布局（相对 GUI 左上角，坐标可调） ============
    // 残页纸（题面，左中上）
    private static final int FRAG_X = 44, FRAG_Y = 12, FRAG_W = 160, FRAG_H = 44;
    // 答题纸（左中下，玩家背包上方）
    private static final int ANS_X = 44, ANS_Y = 66, ANS_W = 160, ANS_H = 58;
    // 墨水瓶（右上角，紧贴右侧列顶部）
    private static final int INK_X = 214, INK_Y = 14, INK_W = 28, INK_H = 34;
    // 词块候选区（右侧列，墨水瓶下方）：两列，chips 32×16
    private static final int BANK_X = 212, BANK_Y = 60, BANK_W = 72;
    private static final int CHIP_W = 32, CHIP_H = 16, CHIP_GAP_X = 4, CHIP_GAP_Y = 2;
    // 答题纸格子：32×18（放 2 字词块），纸内 4 列
    private static final int ANS_CELL_W = 32, ANS_CELL_H = 18;
    // 输入槽右侧的小标签起始 x（槽在 Menu 中 x=16）
    private static final int SLOT_LABEL_X = 40;
    // 玩家背包标题 x（背包 9 列居中于 288 画布 → x=63）
    private static final int INV_LABEL_X = 63, INV_LABEL_Y = 134;

    /** 当前抽中的题（显示层本地随机）；null = 未出题。 */
    private Puzzle activePuzzle;
    /** 当前显示第几句（0 起）；逐句切换由后续判定逻辑驱动，显示层固定第 0 句。 */
    private int sentenceIndex;
    /** 当前句打乱后的候选词块（数量 = 词块数）。null/空 = 未出题。 */
    private List<String> currentTokens;

    public HanmoTaiScreen(HanmoTaiMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_W;
        this.imageHeight = GUI_H;
    }

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

        // 出题状态随残页槽走：放入且未出题 → 随机抽题并打乱词块；取出 → 清空
        updatePuzzleState(hasCanYe);

        // ---------- 残页纸区（题面）：放残页点亮；出题后显示文言 ----------
        drawPaper(guiGraphics, x + FRAG_X, y + FRAG_Y, FRAG_W, FRAG_H,
                hasCanYe ? FRAG_ON : FRAG_OFF, hasCanYe);
        if (hasCanYe && this.activePuzzle != null) {
            drawSentence(guiGraphics, x + FRAG_X, y + FRAG_Y, FRAG_W, FRAG_H);
        }

        // ---------- 答题纸区：放纸点亮；出题后画等量格子 ----------
        drawPaper(guiGraphics, x + ANS_X, y + ANS_Y, ANS_W, ANS_H,
                hasPaper ? ANS_ON : ANS_OFF, hasPaper);
        if (hasPaper && this.currentTokens != null && !this.currentTokens.isEmpty()) {
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
            guiGraphics.fill(x + INK_X + 5, y + INK_Y + 24, x + INK_X + INK_W - 5, y + INK_Y + INK_H, INK_HOLD);
        }

        // ---------- 词块候选区（右侧列）：出题后按本句词块数显示，乱序 ----------
        if (hasCanYe && this.currentTokens != null && !this.currentTokens.isEmpty()) {
            drawTokenBank(guiGraphics, x, y);
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
                INV_LABEL_X, INV_LABEL_Y, TEXT_DARK, false);
    }

    // ============================================================
    // 出题状态（显示层）
    // ============================================================

    /** 放入残页且未出题 → 抽题；取出残页 → 清空出题状态。每帧调用，仅状态变化时实际动作。 */
    private void updatePuzzleState(boolean hasCanYe) {
        if (hasCanYe && this.activePuzzle == null) {
            Puzzle puzzle = PuzzleRegistry.random(RandomSource.create());
            if (puzzle != null && !puzzle.sentences().isEmpty()) {
                this.activePuzzle = puzzle;
                this.sentenceIndex = 0;
                this.currentTokens = shuffle(puzzle.sentences().get(0).tokens());
            }
        } else if (!hasCanYe && this.activePuzzle != null) {
            this.activePuzzle = null;
            this.sentenceIndex = 0;
            this.currentTokens = null;
        }
    }

    /** 打乱词块顺序，作为候选区显示用（答题格数量仍按原词块数）。 */
    private static List<String> shuffle(List<String> tokens) {
        List<String> list = new ArrayList<>(tokens);
        Collections.shuffle(list);
        return list;
    }

    // ============================================================
    // 绘制
    // ============================================================

    /** 题面：右上角"第 N 句"小标 + 文言居中换行显示。 */
    private void drawSentence(GuiGraphics guiGraphics, int px, int py, int pw, int ph) {
        int n = this.sentenceIndex + 1;
        guiGraphics.drawString(this.font, Component.literal("第 " + n + " 句 / 共 3 句"),
                px + 8, py + 3, TEXT_DARK, false);
        String wenyan = this.activePuzzle.sentences().get(this.sentenceIndex).wenyan();
        drawCenteredWrapped(guiGraphics, wenyan, px + 4, py + 17, pw - 8, 13, TEXT_DARK);
    }

    /** 词块候选区：按 currentTokens 顺序（已乱序）两列排 chips，chip 内画词块文字。 */
    private void drawTokenBank(GuiGraphics guiGraphics, int ox, int oy) {
        int n = this.currentTokens.size();
        int rows = (n + 1) / 2;
        // 垂直居中候选区（区内高度约 BANK_Y 到 GUI 底部背包上方）
        int areaTop = BANK_Y;
        int usedH = rows * CHIP_H + Math.max(0, rows - 1) * CHIP_GAP_Y;
        int startY = areaTop + Math.max(0, (72 - usedH) / 2);
        int idx = 0;
        for (int r = 0; r < rows && idx < n; r++) {
            for (int c = 0; c < 2 && idx < n; c++) {
                int cx = ox + BANK_X + c * (CHIP_W + CHIP_GAP_X);
                int cy = oy + startY + r * (CHIP_H + CHIP_GAP_Y);
                // chip 底 + 边框
                guiGraphics.fill(cx, cy, cx + CHIP_W, cy + CHIP_H, CELL_EDGE);
                guiGraphics.fill(cx + 1, cy + 1, cx + CHIP_W - 1, cy + CHIP_H - 1, CELL_IN);
                // 词块文字（两字以内，居中）
                String token = this.currentTokens.get(idx);
                int tx = cx + (CHIP_W - this.font.width(token)) / 2;
                int ty = cy + (CHIP_H - 9) / 2;
                guiGraphics.drawString(this.font, token, tx, ty, CHIP_TEXT, false);
                idx++;
            }
        }
    }

    // ============================================================
    // 通用小工具
    // ============================================================

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

    /** 在纸面内部画等距格子：共 count 个（= 当前句词块数），自动分行并整体居中。 */
    private void drawCells(GuiGraphics guiGraphics, int ox, int oy,
                           int areaX, int areaY, int areaW, int areaH,
                           int cellW, int cellH, int count) {
        int gapX = 4, gapY = 4;
        int innerW = areaW - 16;
        int cols = Math.max(1, (innerW + gapX) / (cellW + gapX)); // 每行最多几格（由纸宽决定）
        cols = Math.min(cols, count);
        int rows = Math.max(1, (count + cols - 1) / cols);
        int usedW = cols * cellW + (cols - 1) * gapX;
        int usedH = rows * cellH + (rows - 1) * gapY;
        int startX = areaX + (areaW - usedW) / 2;                              // 水平居中
        int startY = areaY + Math.max(0, (areaH - usedH) / 2);                 // 垂直居中
        int drawn = 0;
        outer:
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (drawn >= count) {
                    break outer;
                }
                int cx = startX + c * (cellW + gapX);
                int cy = startY + r * (cellH + gapY);
                guiGraphics.fill(ox + cx, oy + cy, ox + cx + cellW, oy + cy + cellH, CELL_EDGE);
                guiGraphics.fill(ox + cx + 1, oy + cy + 1, ox + cx + cellW - 1, oy + cy + cellH - 1, CELL_IN);
                drawn++;
            }
        }
    }

    /** 文言换行绘制（按实际字宽拆行），每行居中。 */
    private void drawCenteredWrapped(GuiGraphics guiGraphics, String text, int x, int y,
                                     int maxW, int lineH, int color) {
        StringBuilder line = new StringBuilder();
        int lineW = 0;
        for (int i = 0; i < text.length(); i++) {
            String ch = text.substring(i, i + 1);
            int chW = this.font.width(ch);
            if (line.length() > 0 && lineW + chW > maxW) {
                drawCenteredLine(guiGraphics, line.toString(), x, y, maxW, color);
                y += lineH;
                line.setLength(0);
                lineW = 0;
            }
            line.append(ch);
            lineW += chW;
        }
        if (line.length() > 0) {
            drawCenteredLine(guiGraphics, line.toString(), x, y, maxW, color);
        }
    }

    private void drawCenteredLine(GuiGraphics guiGraphics, String text, int x, int y,
                                  int maxW, int color) {
        int tx = x + (maxW - this.font.width(text)) / 2;
        guiGraphics.drawString(this.font, text, tx, y, color, false);
    }
}
