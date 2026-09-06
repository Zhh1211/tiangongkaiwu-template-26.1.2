package com.example.tiangongkaiwu.client.gui;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.hanmo.Puzzle;
import com.example.tiangongkaiwu.hanmo.PuzzleRegistry;
import com.example.tiangongkaiwu.hanmo.network.PuzzleDonePayload;
import com.example.tiangongkaiwu.item.ResidualData;
import com.example.tiangongkaiwu.menu.HanmoTaiMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

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
 * 玩法（#21 拖放 + 判定层）：
 *   - 出题：放入残页 → 客户端随机抽一题，逐句显示；取出残页清空。
 *   - 拿起：左键点击候选词块 chip，词块随鼠标浮动（原 chip 位置留空）。
 *   - 落格：把词块拖/放到某个空格上；词块与格子的“字数”必须一致
 *     （格宽已反映答案长度，防止玩家拿长词试塞短格），不一致则拒绝并闪红。
 *   - 换词/取回：拖到已填格会把旧词退回候选区；无拖拽时左键点已填格=取回。
 *   - 判定：所有格填满即自动判定。全对 → 本句成功（金色停留片刻）进下一句；
 *     三句全对 → 通篇译毕（completed，产物结算留给 #22）。有错 → 本句尝试次数+1，
 *     词块逐个“抖动飞回”候选区，候选重新打乱。
 *   - 落空/拖回候选区 = 取消拿起。
 *
 * 宽度策略：一律用 this.font.width(text) 实测宽度 + CELL_PAD 内边距动态决定
 * chip/格子宽；排布逐行贪心、每行居中。chip/cell 矩形相对 GUI 左上角存放于
 * chipBoxes/cellBoxes，绘制与鼠标命中（减去 leftPos/topPos）共用。
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
    private static final int CELL_DONE   = 0xFFE0CD9C; // 已填词块的格子底
    private static final int BANK_EMPTY  = 0xFFBFA775; // 被拿起词块留下的空位底（浅）
    private static final int GOLD_EDGE   = 0xFFC9A227; // 判定正确时的金色描边
    private static final int RED_EDGE    = 0xFFB03020; // 长度不符闪红描边
    private static final int RED_IN      = 0xFFD8A08A; // 长度不符闪红底
    private static final int INK_BODY    = 0xFF17130F; // 墨水瓶身（近黑）
    private static final int INK_HILITE  = 0xFF6E635A; // 瓶身高光
    private static final int INK_HOLD    = 0xFF3C2E1E; // 墨瓶空台（未放墨）
    private static final int TEXT_DARK   = 0xFF3B2C1A; // 深色文字（桌面/纸面上）
    private static final int NOTE_COLOR  = 0xFF7A6344; // 难字注文字（比正文浅的旧墨色）
    private static final int CHIP_TEXT   = 0xFF241A0E; // 词块文字（近黑）
    private static final int FLOAT_EDGE  = 0xFFC9A227; // 随鼠标浮动词块的描边（金，醒目）

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

    /** 判定正确后的金色停留时长（毫秒），随后自动翻下一句。 */
    private static final int SUCCESS_HOLD_MS = 500;
    /** 长度不符/失败的闪红时长（毫秒）。 */
    private static final int FLASH_MS = 450;
    /** 词块抖动飞回动画时长（毫秒）。 */
    private static final int FLYBACK_MS = 550;

    // ============ 出题状态 ============
    /** 当前抽中的题；null = 未出题。 */
    private Puzzle activePuzzle;
    /** 当前显示第几句（0 起）。 */
    private int sentenceIndex;
    /** 当前句已尝试次数（每次填满判错 +1，翻句清零；将来结算经验用）。 */
    private int sentenceAttempts;
    /** 全篇累计失败次数（将来结算经验用）。 */
    private int puzzleWrongTotal;
    /** 三句是否全部译完。 */
    private boolean completed;
    /** 判定正确后的金色停留截止时刻（System.currentTimeMillis）；归零时由渲染帧翻下一句。 */
    private long successHoldUntilMs;
    /** 放入残页但无法出题时的提示（空页/该条目暂无题/已译）；取出残页清空。 */
    private Component noQuestionHint;

    // ============ 词块/格子（下标对齐当前句 tokens） ============
    /** 当前句候选词块（乱序）；null = 未出题/已译毕。 */
    private List<String> currentTokens;
    /** 每个答题格的已填词块；下标 i ↔ 正确顺序 tokens 第 i 个；null = 空。 */
    private String[] cellFill;
    /** 候选 chip 矩形（相对 GUI 左上）；绘制与拖放命中共用。 */
    private final List<int[]> chipBoxes = new ArrayList<>();
    /** 答题格矩形（相对 GUI 左上）；绘制与拖放命中共用。 */
    private final List<int[]> cellBoxes = new ArrayList<>();

    // ============ 拖放/动画状态 ============
    /** 拿起中的词块文本；null = 未拿起。 */
    private String picked;
    /** 拿起词块在 currentTokens 中的下标（移除用）；-1 = 未拿起。 */
    private int pickedIndex = -1;
    /** 抖动飞回候选区的动画（判定失败）。 */
    private final List<FlyBack> flyBacks = new ArrayList<>();
    /** 闪红格（长度不符）：{格下标, 过期时刻 ms}。 */
    private final List<long[]> cellFlashes = new ArrayList<>();
    /** 最近一次鼠标屏幕坐标（渲染时更新，供浮动词块跟随）。 */
    private int lastMouseX, lastMouseY;

    public HanmoTaiScreen(HanmoTaiMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_W;
        this.imageHeight = GUI_H;
    }

    // ============================================================
    // 生命周期
    // ============================================================

    /** 每渲染帧推进：金色停留→翻句、闪红/飞回动画按墙钟过期清理。 */
    private void updateTimedState() {
        long now = System.currentTimeMillis();
        if (!this.completed && this.activePuzzle != null && this.successHoldUntilMs != 0 && now >= this.successHoldUntilMs) {
            this.successHoldUntilMs = 0;
            advanceSentence();
        }
        this.cellFlashes.removeIf(f -> now >= f[1]);
        this.flyBacks.removeIf(fb -> now >= fb.beginMs + fb.durationMs);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.updateTimedState();
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        // 1.21.1 的 AbstractContainerScreen.render() 已不再自动调用 renderTooltip
        // （只设置 hoveredSlot 与画槽位高亮），需子类在渲染末尾补一次。
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    // ============================================================
    // 鼠标交互（词块区/答题格；其余交给父类处理背包槽点击）
    // ============================================================

    /** 左键：无拖拽时点候选 chip = 拿起；点已填格 = 取回候选区。 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInteractive()) {
            int rx = (int) mouseX - this.leftPos;
            int ry = (int) mouseY - this.topPos;
            rebuildBankLayout();
            rebuildCellLayout();
            if (this.picked == null) {
                // 拿起候选词块
                int k = hitChip(rx, ry);
                if (k >= 0) {
                    this.picked = this.currentTokens.get(k);
                    this.pickedIndex = k;
                    return true;
                }
                // 点已填格 → 取回候选（放回并重新打乱）
                int ci = hitFilledCell(rx, ry);
                if (ci >= 0) {
                    returnCellToBank(ci);
                    return true;
                }
            }
            // picked != null 时本轮不换拿，交给 mouseReleased 落子/取消
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 左键松开：把拿起中的词块落下（空格/换格），或取消。 */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.picked != null) {
            if (isInteractive()) {
                int rx = (int) mouseX - this.leftPos;
                int ry = (int) mouseY - this.topPos;
                rebuildCellLayout();
                int cellIdx = hitAnyCell(rx, ry);
                if (cellIdx >= 0) {
                    dropPickedOnto(cellIdx);
                } else {
                    // 落在非格区域（含拖回候选区）= 取消，词块回到原 chip 空位
                    this.picked = null;
                    this.pickedIndex = -1;
                }
            } else {
                // 拿起途中材料被取走/已译毕等 → 取消拿起，避免词块悬浮卡住
                this.picked = null;
                this.pickedIndex = -1;
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 词块区/格区可交互的前提：已出题、未译毕、不在金色停留中、残页/纸/墨三样齐（译毕要消耗它们）。 */
    private boolean isInteractive() {
        return this.activePuzzle != null && !this.completed && !successHoldActive()
                && this.currentTokens != null && hasPaperInSlot() && hasInkInSlot();
    }

    /** 墨槽是否放了墨（原版墨囊或松烟墨）。 */
    private boolean hasInkInSlot() {
        return !this.menu.slots.get(HanmoTaiMenu.SLOT_INK).getItem().isEmpty();
    }

    /** 答题纸槽是否放了纸（原版纸或宣纸）。 */
    private boolean hasPaperInSlot() {
        return !this.menu.slots.get(HanmoTaiMenu.SLOT_PAPER).getItem().isEmpty();
    }

    /** 金色停留（判定正确后翻句前的短暂反馈）是否仍在进行。 */
    private boolean successHoldActive() {
        return this.successHoldUntilMs > System.currentTimeMillis();
    }

    /** 点击候选 chip：返回其在 currentTokens 的下标，无则 -1。 */
    private int hitChip(int rx, int ry) {
        for (int i = 0; i < this.chipBoxes.size(); i++) {
            int[] b = this.chipBoxes.get(i);
            if (rx >= b[0] && rx < b[0] + b[2] && ry >= b[1] && ry < b[1] + b[3]) {
                return i;
            }
        }
        return -1;
    }

    /** 命中任意答题格（空或已填）返回其下标，无则 -1。 */
    private int hitAnyCell(int rx, int ry) {
        for (int i = 0; i < this.cellBoxes.size(); i++) {
            int[] b = this.cellBoxes.get(i);
            if (rx >= b[0] && rx < b[0] + b[2] && ry >= b[1] && ry < b[1] + b[3]) {
                return i;
            }
        }
        return -1;
    }

    /** 命中“已填词”的格返回其下标，无则 -1。 */
    private int hitFilledCell(int rx, int ry) {
        int i = hitAnyCell(rx, ry);
        return (i >= 0 && this.cellFill != null && this.cellFill[i] != null) ? i : -1;
    }

    /** 把格 i 的词块退回候选区（放回并打乱）。 */
    private void returnCellToBank(int i) {
        if (this.cellFill == null || i < 0 || i >= this.cellFill.length || this.cellFill[i] == null) {
            return;
        }
        this.currentTokens.add(this.cellFill[i]);
        this.cellFill[i] = null;
        shuffleTokens();
    }

    /**
     * 把拿起中的词块落到格 i：若格内已有别的词先退回；然后按“字数”硬校验
     * （词块字数 == 该格答案字数，配合等长格子防作弊），不符则拒绝并闪红格。
     */
    private void dropPickedOnto(int i) {
        if (this.cellFill == null || i < 0 || i >= this.cellFill.length) {
            this.picked = null;
            this.pickedIndex = -1;
            return;
        }
        String tok = this.picked;
        int pk = this.pickedIndex;
        String expected = answerTokens().get(i);
        String old = this.cellFill[i];

        // 先把格内旧词（若不同）退回候选
        if (old != null && !old.equals(tok)) {
            this.cellFill[i] = null;
            this.currentTokens.add(old);
        }
        boolean lenOk = tok.length() == expected.length();
        if (lenOk) {
            // 放置成功
            this.cellFill[i] = tok;
            this.currentTokens.remove(pk);
            this.picked = null;
            this.pickedIndex = -1;
            if (old != null && !old.equals(tok)) {
                shuffleTokens(); // 有换入旧词，重排候选
            }
            maybeJudge();
        } else {
            // 长度不符：词块回原位（取消拿起），该格闪红
            this.picked = null;
            this.pickedIndex = -1;
            if (old != null && !old.equals(tok)) {
                shuffleTokens();
            }
            flashCell(i);
        }
    }

    /** 格子全填满时自动判定；未填满则无事。 */
    private void maybeJudge() {
        if (this.cellFill == null) {
            return;
        }
        for (String t : this.cellFill) {
            if (t == null) {
                return;
            }
        }
        List<String> answer = answerTokens();
        boolean allRight = true;
        for (int i = 0; i < answer.size(); i++) {
            if (!answer.get(i).equals(this.cellFill[i])) {
                allRight = false;
                break;
            }
        }
        if (allRight) {
            if (this.sentenceIndex >= this.activePuzzle.sentences().size() - 1) {
                completePuzzle(); // 末句也对 → 通篇译毕
            } else {
                this.successHoldUntilMs = System.currentTimeMillis() + SUCCESS_HOLD_MS; // 金色停留后翻句
            }
        } else {
            failAttempt();
        }
    }

    /** 本句判定失败：尝试 +1，词块逐个抖动飞回候选区，候选重打乱。 */
    private void failAttempt() {
        this.sentenceAttempts++;
        this.puzzleWrongTotal++;
        // 先把候选换成“整句重新打乱”，随后动画目标指向新 chip 位置
        List<String> all = new ArrayList<>(answerTokens());
        this.currentTokens = shuffle(all);
        rebuildBankLayout();
        // 生成飞回动画：源 = 各格中心，终点 = 该词块在新候选区 chip 的中心
        if (this.cellFill != null) {
            for (int i = 0; i < this.cellFill.length; i++) {
                String t = this.cellFill[i];
                if (t == null) {
                    continue;
                }
                int[] cb = i < this.cellBoxes.size() ? this.cellBoxes.get(i) : null;
                int idx = this.currentTokens.indexOf(t);
                int[] chip = (idx >= 0 && idx < this.chipBoxes.size()) ? this.chipBoxes.get(idx) : null;
                if (cb != null && chip != null) {
                    this.flyBacks.add(new FlyBack(t,
                            cb[0] + cb[2] / 2f, cb[1] + cb[3] / 2f,
                            chip[0] + chip[2] / 2f, chip[1] + chip[3] / 2f,
                            System.currentTimeMillis(), FLYBACK_MS, (float) Math.random() * 100));
                }
            }
            java.util.Arrays.fill(this.cellFill, null);
        }
        this.picked = null;
        this.pickedIndex = -1;
        this.cellFlashes.clear(); // 旧的闪红一并清掉
    }

    /**
     * 三句全对：置完成态、清掉一切可交互元素，并通知服务端誊录结算
     * （服务端校验后消耗墨/纸、把残页翻转为已译并烙上经验，见 PuzzleSettlement）。
     */
    private void completePuzzle() {
        this.completed = true;
        // 先取题目信息上报；activePuzzle 保留以便完成语持续显示
        if (this.activePuzzle != null) {
            PacketDistributor.sendToServer(new PuzzleDonePayload(this.activePuzzle.id(), this.puzzleWrongTotal));
        }
        this.currentTokens = null;
        this.cellFill = null;
        this.picked = null;
        this.pickedIndex = -1;
        this.successHoldUntilMs = 0;
        this.chipBoxes.clear();
        this.cellBoxes.clear();
        this.flyBacks.clear();
        this.cellFlashes.clear();
    }

    /** 本句通过：翻到下一句并初始化其词块/格子。 */
    private void advanceSentence() {
        this.sentenceIndex++;
        this.sentenceAttempts = 0;
        this.successHoldUntilMs = 0;
        this.cellFlashes.clear();
        this.flyBacks.clear();
        List<String> next = this.activePuzzle.sentences().get(this.sentenceIndex).tokens();
        this.cellFill = new String[next.size()];
        this.currentTokens = shuffle(next);
        this.chipBoxes.clear();
        this.cellBoxes.clear();
    }

    // ============================================================
    // 出题状态
    // ============================================================

    /**
     * 放入残页且未出题 → 按其组件目标条目筛题池抽题（不再全库乱抽）；
     * 空页/该条目暂无题/已译残页 → 设提示不抽题；取出残页 → 清空。
     * 每帧调用，仅状态变化时实际动作。
     */
    private void updatePuzzleState(boolean hasCanYe) {
        if (hasCanYe) {
            ItemStack frag = this.menu.slots.get(HanmoTaiMenu.SLOT_CAN_YE).getItem();
            if (this.activePuzzle == null) {
                ResidualData data = frag.get(TiangongKaiwu.RESIDUAL.get());
                if (data == null || data.entry().isBlank()) {
                    this.noQuestionHint = Component.translatable("item.tiangongkaiwu.can_ye.use.untargeted");
                } else if (data.translated()) {
                    this.noQuestionHint = Component.translatable("item.tiangongkaiwu.can_ye.status.done_hint");
                } else {
                    List<Puzzle> pool = PuzzleRegistry.byEntry(data.entry());
                    if (pool.isEmpty()) {
                        this.noQuestionHint = Component.translatable("gui.tiangongkaiwu.hanmo_no_question");
                    } else {
                        this.noQuestionHint = null;
                        initPuzzle(pool.get(RandomSource.create().nextInt(pool.size())));
                    }
                }
            }
        } else if (this.activePuzzle != null || this.noQuestionHint != null) {
            clearPuzzle();
            this.noQuestionHint = null;
        }
    }

    private void initPuzzle(Puzzle puzzle) {
        this.activePuzzle = puzzle;
        this.sentenceIndex = 0;
        this.sentenceAttempts = 0;
        this.puzzleWrongTotal = 0;
        this.completed = false;
        this.successHoldUntilMs = 0;
        this.picked = null;
        this.pickedIndex = -1;
        this.flyBacks.clear();
        this.cellFlashes.clear();
        List<String> first = puzzle.sentences().get(0).tokens();
        this.cellFill = new String[first.size()];
        this.currentTokens = shuffle(first);
        this.chipBoxes.clear();
        this.cellBoxes.clear();
    }

    private void clearPuzzle() {
        this.activePuzzle = null;
        this.sentenceIndex = 0;
        this.sentenceAttempts = 0;
        this.puzzleWrongTotal = 0;
        this.completed = false;
        this.successHoldUntilMs = 0;
        this.currentTokens = null;
        this.cellFill = null;
        this.picked = null;
        this.pickedIndex = -1;
        this.chipBoxes.clear();
        this.cellBoxes.clear();
        this.flyBacks.clear();
        this.cellFlashes.clear();
    }

    /** 当前句的正确词块列表（答案顺序）。 */
    private List<String> answerTokens() {
        return this.activePuzzle.sentences().get(this.sentenceIndex).tokens();
    }

    /** 打乱词块顺序，作为候选区显示用（答题格数量仍按原词块数）。 */
    private static List<String> shuffle(List<String> tokens) {
        List<String> list = new ArrayList<>(tokens);
        Collections.shuffle(list);
        return list;
    }

    /** 候选列表重打乱（交换/取回/失败重排时调用）。 */
    private void shuffleTokens() {
        this.currentTokens = shuffle(this.currentTokens);
        this.picked = null;
        this.pickedIndex = -1;
        rebuildBankLayout();
    }

    private void flashCell(int i) {
        long end = System.currentTimeMillis() + FLASH_MS;
        for (long[] f : this.cellFlashes) {
            if (f[0] == i) {
                f[1] = end;
                return;
            }
        }
        this.cellFlashes.add(new long[] { i, end });
    }

    private boolean isFlashing(int i) {
        long now = System.currentTimeMillis();
        for (long[] f : this.cellFlashes) {
            if (f[0] == i && now < f[1]) {
                return true;
            }
        }
        return false;
    }

    // ============================================================
    // 布局缓存（与绘制解耦，保证鼠标事件时 box 与当前句一致）
    // ============================================================

    /** 重算候选 chip 矩形 → chipBoxes（当前Tokens 为 null 时清空）。 */
    private void rebuildBankLayout() {
        this.chipBoxes.clear();
        if (this.currentTokens == null || this.currentTokens.isEmpty()) {
            return;
        }
        int maxW = BANK_W - 8;
        List<List<Integer>> rows = new ArrayList<>();
        List<Integer> cur = new ArrayList<>();
        int curW = 0;
        for (int i = 0; i < this.currentTokens.size(); i++) {
            int w = chipWidth(this.currentTokens.get(i));
            if (!cur.isEmpty() && curW + BANK_GAP_X + w > maxW) {
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
                rowW += chipWidth(this.currentTokens.get(i));
            }
            rowW += BANK_GAP_X * (row.size() - 1);
            int cx = BANK_X + (BANK_W - rowW) / 2;
            for (int i : row) {
                int w = chipWidth(this.currentTokens.get(i));
                this.chipBoxes.add(new int[] { cx, y, w, CHIP_H });
                cx += w + BANK_GAP_X;
            }
            y += CHIP_H + BANK_GAP_Y;
        }
    }

    /** 重算答题格矩形 → cellBoxes（未出题/无格时清空）。 */
    private void rebuildCellLayout() {
        this.cellBoxes.clear();
        if (this.activePuzzle == null || this.completed) {
            return;
        }
        int maxW = ANS_W - 16;
        List<List<Integer>> rows = new ArrayList<>();
        List<Integer> cur = new ArrayList<>();
        int curW = 0;
        for (int i = 0; i < answerTokens().size(); i++) {
            int w = chipWidth(answerTokens().get(i));
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
        int usedH = rows.size() * ANS_CELL_H + Math.max(0, rows.size() - 1) * CELL_GAP_Y;
        int y = ANS_Y + Math.max(6, (ANS_H - usedH) / 2);
        for (List<Integer> row : rows) {
            int rowW = 0;
            for (int i : row) {
                rowW += chipWidth(answerTokens().get(i));
            }
            rowW += CELL_GAP_X * (row.size() - 1);
            int cx = ANS_X + (ANS_W - rowW) / 2;
            for (int i : row) {
                int w = chipWidth(answerTokens().get(i));
                this.cellBoxes.add(new int[] { cx, y, w, ANS_CELL_H });
                cx += w + CELL_GAP_X;
            }
            y += ANS_CELL_H + CELL_GAP_Y;
        }
    }

    // ============================================================
    // 渲染
    // ============================================================

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
        } else if (hasCanYe && this.noQuestionHint != null) {
            // 放了残页但抽不了题：残页纸上显示原因（空页/该条目暂无题/已译）
            drawCenteredWrapped(guiGraphics, this.noQuestionHint.getString(),
                    x + FRAG_X + 4, y + FRAG_Y + 14, FRAG_W - 8, 13, NOTE_COLOR);
        }

        // ---------- 答题纸区：放纸点亮；出题后画格子（宽按正确词块文本） ----------
        drawPaper(guiGraphics, x + ANS_X, y + ANS_Y, ANS_W, ANS_H,
                hasPaper ? ANS_ON : ANS_OFF, hasPaper);
        if (hasPaper && this.activePuzzle != null && !this.completed) {
            drawCells(guiGraphics, x, y);
        }

        // ---------- 墨水瓶（右上角）：放入墨后点亮为墨瓶 ----------
        if (hasInk) {
            int bx = x + INK_X, by = y + INK_Y;
            guiGraphics.fill(bx + 3, by + 6, bx + INK_W - 3, by + INK_H, INK_BODY);
            guiGraphics.fill(bx + 5, by + 8, bx + 7, by + INK_H - 2, INK_HILITE);
            guiGraphics.fill(bx + 7, by + 3, bx + INK_W - 7, by + 8, INK_BODY);
            guiGraphics.fill(bx + 6, by, bx + INK_W - 6, by + 3, WOOD_EDGE);
        } else {
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

        // ---------- 叠加层：飞回动画 > 浮动词块（置顶） ----------
        for (FlyBack fb : this.flyBacks) {
            paintFlyBack(guiGraphics, x, y, fb);
        }
        if (this.picked != null) {
            paintFloatingChip(guiGraphics);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 8, 6, TEXT_DARK, false);
        drawSlotLabel(guiGraphics, HanmoTaiMenu.SLOT_CAN_YE, "gui.tiangongkaiwu.hanmo_slot_canye");
        drawSlotLabel(guiGraphics, HanmoTaiMenu.SLOT_INK, "gui.tiangongkaiwu.hanmo_slot_ink");
        drawSlotLabel(guiGraphics, HanmoTaiMenu.SLOT_PAPER, "gui.tiangongkaiwu.hanmo_slot_paper");
        guiGraphics.drawString(this.font, Component.translatable("container.inventory"),
                INV_LABEL_X, INV_LABEL_Y, TEXT_DARK, false);
    }

    /** 题面：第 N 句小标 + 文言居中；译毕时改为完成语。文言下方依 notes 画难字注（一行一条）。 */
    private void drawSentence(GuiGraphics guiGraphics, int px, int py, int pw, int ph) {
        if (this.completed) {
            drawCenteredWrapped(guiGraphics, Component.translatable("gui.tiangongkaiwu.hanmo_done").getString(),
                    px + 4, py + 14, pw - 8, 13, TEXT_DARK);
            return;
        }
        int n = this.sentenceIndex + 1;
        int total = this.activePuzzle.sentences().size();
        guiGraphics.drawString(this.font,
                Component.translatable("gui.tiangongkaiwu.hanmo_sentence", n, total),
                px + 8, py + 3, TEXT_DARK, false);
        String wenyan = this.activePuzzle.sentences().get(this.sentenceIndex).wenyan();
        // 返回文言排完后的行底，供难字注接续排版（文言多行时自动下移/放不下则不再画注）
        int wenyanEndY = drawCenteredWrapped(guiGraphics, wenyan, px + 4, py + 17, pw - 8, 13, TEXT_DARK);
        List<String> notes = this.activePuzzle.sentences().get(this.sentenceIndex).notes();
        drawNotes(guiGraphics, px, py, pw, ph, wenyanEndY, notes);
    }

    /**
     * 残页卡内难字注：紧跟文言排完的行底显示，每条注单独一行。
     * 单行超宽按字符截断（防溢出到纸上边界外），纸底余位不足则放弃。
     */
    private void drawNotes(GuiGraphics guiGraphics, int px, int py, int pw, int ph,
                           int startY, List<String> notes) {
        if (notes == null || notes.isEmpty()) {
            return;
        }
        String prefix = Component.translatable("gui.tiangongkaiwu.hanmo_note").getString();
        int maxW = pw - 8;
        int x = px + 4;
        int y = startY + 1;
        for (String note : notes) {
            if (y + 13 > py + ph) {
                break; // 纸底余位不足
            }
            String text = prefix + note;
            // 单行内截断，绝不让注换行溢出残页纸
            while (this.font.width(text) > maxW && text.length() > prefix.length()) {
                text = text.substring(0, text.length() - 1);
            }
            if (text.length() > prefix.length()) {
                int tx = x + (maxW - this.font.width(text)) / 2;
                guiGraphics.drawString(this.font, text, tx, y, NOTE_COLOR, false);
            }
            y += 13;
        }
    }

    /** 词块文本 → chip/格子宽：实测字宽 + 左右内边距。多语言/任意词长自适应。 */
    private int chipWidth(String token) {
        return this.font.width(token) + CELL_PAD * 2;
    }

    /** 候选词块区绘制（布局已由 rebuildBankLayout 缓存到 chipBoxes）。 */
    private void drawTokenBank(GuiGraphics guiGraphics, int ox, int oy) {
        rebuildBankLayout();
        List<String> tokens = this.currentTokens;
        for (int i = 0; i < this.chipBoxes.size(); i++) {
            int[] b = this.chipBoxes.get(i);
            String token = tokens.get(i);
            boolean vacated = (i == this.pickedIndex);
            // chip 底 + 边框（被拿起的 chip 留浅色空位框）
            int edge = vacated ? GOLD_EDGE : CELL_EDGE;
            int inner = vacated ? BANK_EMPTY : CELL_IN;
            guiGraphics.fill(ox + b[0], oy + b[1], ox + b[0] + b[2], oy + b[1] + b[3], edge);
            guiGraphics.fill(ox + b[0] + 1, oy + b[1] + 1, ox + b[0] + b[2] - 1, oy + b[1] + b[3] - 1, inner);
            if (!vacated) {
                drawTokenCentered(guiGraphics, token, ox + b[0], oy + b[1], b[2], b[3], CHIP_TEXT);
            }
        }
    }

    /** 答题格绘制（布局已由 rebuildCellLayout 缓存到 cellBoxes）。 */
    private void drawCells(GuiGraphics guiGraphics, int ox, int oy) {
        rebuildCellLayout();
        List<String> answer = answerTokens();
        for (int i = 0; i < this.cellBoxes.size(); i++) {
            int[] b = this.cellBoxes.get(i);
            boolean filled = this.cellFill != null && this.cellFill[i] != null;
            boolean gold = successHoldActive();
            int edge = CELL_EDGE;
            int inner = CELL_IN;
            if (isFlashing(i)) {
                edge = RED_EDGE;
                inner = RED_IN;
            } else if (gold) {
                edge = GOLD_EDGE;
            } else if (filled) {
                inner = CELL_DONE;
            }
            guiGraphics.fill(ox + b[0], oy + b[1], ox + b[0] + b[2], oy + b[1] + b[3], edge);
            guiGraphics.fill(ox + b[0] + 1, oy + b[1] + 1, ox + b[0] + b[2] - 1, oy + b[1] + b[3] - 1, inner);
            if (filled) {
                drawTokenCentered(guiGraphics, this.cellFill[i], ox + b[0], oy + b[1], b[2], b[3], CHIP_TEXT);
            }
        }
    }

    /** 画一次飞回动画（块 + 文字，外层可触达 font）。 */
    private void paintFlyBack(GuiGraphics guiGraphics, int ox, int oy, FlyBack fb) {
        long now = System.currentTimeMillis();
        long elapsed = now - fb.beginMs;
        float p = Math.min(1f, (float) elapsed / fb.durationMs); // 0→1
        float remain = 1f - p;                                   // 1→0
        // 抖动：幅度随剩余时间衰减
        float jx = (float) Math.sin(fb.seed + elapsed * 0.09f) * 2.5f * remain;
        float jy = (float) Math.cos(fb.seed * 1.3f + elapsed * 0.11f) * 1.2f * remain;
        // 缓出位移
        float e = 1f - (1f - p) * (1f - p) * (1f - p);
        float px = fb.sx + (fb.tx - fb.sx) * e + jx;
        float py = fb.sy + (fb.ty - fb.sy) * e + jy;
        int w = chipWidth(fb.token);
        int bx = ox + (int) (px - w / 2f);
        int by = oy + (int) (py - CHIP_H / 2f);
        guiGraphics.fill(bx, by, bx + w, by + CHIP_H, CELL_EDGE);
        guiGraphics.fill(bx + 1, by + 1, bx + w - 1, by + CHIP_H - 1, CELL_DONE);
        drawTokenCentered(guiGraphics, fb.token, bx, by, w, CHIP_H, CHIP_TEXT);
    }

    /** 随鼠标浮动的词块（屏幕坐标居中于光标）。 */
    private void paintFloatingChip(GuiGraphics guiGraphics) {
        String token = this.picked;
        int w = chipWidth(token);
        int h = CHIP_H;
        int fx = this.lastMouseX - w / 2;
        int fy = this.lastMouseY - h / 2;
        guiGraphics.fill(fx, fy, fx + w, fy + h, FLOAT_EDGE);
        guiGraphics.fill(fx + 1, fy + 1, fx + w - 1, fy + h - 1, CELL_IN);
        drawTokenCentered(guiGraphics, token, fx, fy, w, h, CHIP_TEXT);
    }

    private void drawTokenCentered(GuiGraphics guiGraphics, String text, int bx, int by, int w, int h, int color) {
        int tx = bx + (w - this.font.width(text)) / 2;
        int ty = by + (h - this.font.lineHeight) / 2;
        guiGraphics.drawString(this.font, text, tx, ty, color, false);
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
            guiGraphics.fill(px + 2, py + 2, px + 10, py + 5, 0x30FFFFFF);
        }
    }

    /** 通用换行绘制（按实际字宽拆行），每行居中。返回排完后的下一行顶部 y。 */
    private int drawCenteredWrapped(GuiGraphics guiGraphics, String text, int x, int y,
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
            y += lineH;
        }
        return y;
    }

    private void drawCenteredLine(GuiGraphics guiGraphics, String text, int x, int y,
                                  int maxW, int color) {
        int tx = x + (maxW - this.font.width(text)) / 2;
        guiGraphics.drawString(this.font, text, tx, y, color, false);
    }

    // ============================================================
    // 动画：词块从格子“抖动飞回”候选区
    // ============================================================

    /** 一次飞回动画：起点 = 格中心，终点 = 候选 chip 中心；前半段抖动渐弱、后半段缓入落位。 */
    private static final class FlyBack {
        final String token;
        final float sx, sy;
        final float tx, ty;
        final long beginMs;
        final long durationMs;
        final float seed;

        FlyBack(String token, float sx, float sy, float tx, float ty, long beginMs, long durationMs, float seed) {
            this.token = token;
            this.sx = sx;
            this.sy = sy;
            this.tx = tx;
            this.ty = ty;
            this.beginMs = beginMs;
            this.durationMs = durationMs;
            this.seed = seed;
        }
    }
}
