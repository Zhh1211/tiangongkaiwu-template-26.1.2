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
 *   - 右列：词块候选区 —— 出题后按本句词块数显示，chip 宽度按词块文本自动计算
 *   - 左中下：答题纸 —— 放入纸后点亮，格子数 = 本句词块数，格子宽度按
 *     正确顺序对应词块的文本宽度计算（玩家可看出每个空该填多长的词）
 *   - 底部：玩家背包 3×9 + 快捷栏（水平居中，由父类按 Menu 槽坐标渲染）
 *
 * 出题流程（显示层，拖放/判定在后续任务）：放入残页 → 从客户端题库随机抽一题，
 * 显示第一句文言与乱序词块；词块与格子数量都由当前句词块数决定；
 * 取出残页即清空状态。
 *
 * 宽度策略：不按"假设 2 字/固定 32px"硬编码，一律用 this.font.width(text)
 * 实测文本宽度 + CELL_PAD 内边距动态决定 chip/格子宽，任意语言/词长自适应。
 * 排布为逐行贪心（放不下换行），每行水平居中；每个 chip/格子的矩形会存进
 * chipBoxes/cellBoxes（相对 GUI 左上角），供绘制与后续拖放命中测试复用。
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
    // 词块候选区（右侧列，墨水瓶下方）
    private static final int BANK_X = 212, BANK_Y = 58, BANK_W = 72;
    // 词块 chip / 答题格尺寸与间距（宽度按文本动态，这里只有固定向与间距）
    private static final int CHIP_H = 16;
    private static final int ANS_CELL_H = 18;
    private static final int CELL_PAD = 6;      // 文字与 chip/格边框的单侧内边距（左右各 PAD）
    private static final int BANK_GAP_X = 4, BANK_GAP_Y = 2;   // chip 横向/纵向间距
    private static final int CELL_GAP_X = 4, CELL_GAP_Y = 4;   // 答题格横向/纵向间距
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
    /** 候选词块 chip 矩形（相对 GUI 左上），下标对齐 currentTokens；绘制与拖放命中共用。 */
    private final List<int[]> chipBoxes = new ArrayList<>();
    /** 答题格矩形（相对 GUI 左上），下标 i 对应正确顺序 tokens 第 i 个；同上。 */
    private final List<int[]> cellBoxes = new ArrayList<>();

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

        // ---------- 答题纸区：放纸点亮；出题后画格子（宽按正确词块文本） ----------
        drawPaper(guiGraphics, x + ANS_X, y + ANS_Y, ANS_W, ANS_H,
                hasPaper ? ANS_ON : ANS_OFF, hasPaper);
        if (hasPaper && this.activePuzzle != null) {
            List<String> answer = this.activePuzzle.sentences().get(this.sentenceIndex).tokens();
            drawCells(guiGraphics, x, y, ANS_X, ANS_Y, ANS_W, ANS_H, ANS_CELL_H, answer);
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
            this.chipBoxes.clear();
            this.cellBoxes.clear();
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
        int total = this.activePuzzle.sentences().size();
        guiGraphics.drawString(this.font,
                Component.translatable("gui.tiangongkaiwu.hanmo_sentence", n, total),
                px + 8, py + 3, TEXT_DARK, false);
        String wenyan = this.activePuzzle.sentences().get(this.sentenceIndex).wenyan();
        drawCenteredWrapped(guiGraphics, wenyan, px + 4, py + 17, pw - 8, 13, TEXT_DARK);
    }

    /** 词块文本 → chip/格子宽：实测字宽 + 左右内边距。多语言/任意词长自适应。 */
    private int chipWidth(String token) {
        return this.font.width(token) + CELL_PAD * 2;
    }

    /** 词块 chip 候选区：按词块文本宽度逐行贪心排放，每行居中；矩形存入 chipBoxes。 */
    private void drawTokenBank(GuiGraphics guiGraphics, int ox, int oy) {
        this.chipBoxes.clear();
        List<String> tokens = this.currentTokens;
        int maxW = BANK_W - 8; // 区内左右各 4px 内边
        // 贪心分行：每行记录 token 下标，行内宽度累计
        List<List<Integer>> rows = new ArrayList<>();
        List<Integer> cur = new ArrayList<>();
        int curW = 0;
        for (int i = 0; i < tokens.size(); i++) {
            int w = chipWidth(tokens.get(i));
            int addW = cur.isEmpty() ? 0 : BANK_GAP_X;
            if (!cur.isEmpty() && curW + addW + w > maxW) {
                rows.add(cur);
                cur = new ArrayList<>();
                curW = 0;
            }
            if (!cur.isEmpty()) {
                curW += BANK_GAP_X;
            }
            cur.add(i);
            curW += w;
        }
        if (!cur.isEmpty()) {
            rows.add(cur);
        }
        int y = BANK_Y;
        for (List<Integer> row : rows) {
            int rowW = 0;
            for (int i : row) {
                rowW += chipWidth(tokens.get(i));
            }
            rowW += BANK_GAP_X * (row.size() - 1);
            int startX = BANK_X + (BANK_W - rowW) / 2; // 行内水平居中
            int cx = startX;
            for (int i : row) {
                String token = tokens.get(i);
                int w = chipWidth(token);
                // chip 底 + 边框
                guiGraphics.fill(ox + cx, oy + y, ox + cx + w, oy + y + CHIP_H, CELL_EDGE);
                guiGraphics.fill(ox + cx + 1, oy + y + 1, ox + cx + w - 1, oy + y + CHIP_H - 1, CELL_IN);
                // 词块文字居中
                int tx = cx + (w - this.font.width(token)) / 2;
                int ty = y + (CHIP_H - this.font.lineHeight) / 2;
                guiGraphics.drawString(this.font, token, ox + tx, oy + ty, CHIP_TEXT, false);
                this.chipBoxes.add(new int[] { cx, y, w, CHIP_H });
                cx += w + BANK_GAP_X;
            }
            y += CHIP_H + BANK_GAP_Y;
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

    /**
     * 在答题纸内画格子：格数 = 词块数，每格宽 = font.width(对应词块) + 内边距，
     * 即玩家能看出每个空位应填多长的词。逐行贪心换行、每行水平居中、整体垂直居中。
     * 矩形存入 cellBoxes（下标 i 对应 tokens 第 i 个）。
     *
     * 注：词块 ≤ 2 行容量由纸高（58px，2 行 18px+4px 间距）决定；若未来题库出现
     * 超长词块使行数超过 2，需扩大 ANS 区或引入滚动，暂不做。
     */
    private void drawCells(GuiGraphics guiGraphics, int ox, int oy,
                           int areaX, int areaY, int areaW, int areaH,
                           int cellH, List<String> tokens) {
        this.cellBoxes.clear();
        int maxW = areaW - 16; // 纸内左右各 8px
        // 贪心分行
        List<List<Integer>> rows = new ArrayList<>();
        List<Integer> cur = new ArrayList<>();
        int curW = 0;
        for (int i = 0; i < tokens.size(); i++) {
            int w = chipWidth(tokens.get(i));
            if (!cur.isEmpty() && curW + CELL_GAP_X + w > maxW) {
                rows.add(cur);
                cur = new ArrayList<>();
                curW = 0;
            }
            if (!cur.isEmpty()) {
                curW += CELL_GAP_X;
            }
            cur.add(i);
            curW += w;
        }
        if (!cur.isEmpty()) {
            rows.add(cur);
        }
        int usedH = rows.size() * cellH + Math.max(0, rows.size() - 1) * CELL_GAP_Y;
        int startY = areaY + Math.max(6, (areaH - usedH) / 2); // 垂直居中，至少离纸顶 6px
        int y = startY;
        for (List<Integer> row : rows) {
            int rowW = 0;
            for (int i : row) {
                rowW += chipWidth(tokens.get(i));
            }
            rowW += CELL_GAP_X * (row.size() - 1);
            int startX = areaX + (areaW - rowW) / 2; // 行内水平居中
            int cx = startX;
            for (int i : row) {
                int w = chipWidth(tokens.get(i));
                guiGraphics.fill(ox + cx, oy + y, ox + cx + w, oy + y + cellH, CELL_EDGE);
                guiGraphics.fill(ox + cx + 1, oy + y + 1, ox + cx + w - 1, oy + y + cellH - 1, CELL_IN);
                this.cellBoxes.add(new int[] { cx, y, w, cellH });
                cx += w + CELL_GAP_X;
            }
            y += cellH + CELL_GAP_Y;
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
