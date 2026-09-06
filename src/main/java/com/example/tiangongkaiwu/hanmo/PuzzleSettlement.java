package com.example.tiangongkaiwu.hanmo;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.hanmo.network.PuzzleDonePayload;
import com.example.tiangongkaiwu.hanmo.network.SettlementResultPayload;
import com.example.tiangongkaiwu.item.ResidualData;
import com.example.tiangongkaiwu.menu.HanmoTaiMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

/**
 * 翰墨台誊录结算（服务端权威）。
 *
 * 客户端三句全对后上报 {@link PuzzleDonePayload}；此处校验：
 *   玩家开着的容器确是翰墨台、残页槽为待译残页、其组件条目与上报题目一致、墨与纸都就位。
 * 通过则：消耗 1 墨 + 1 纸；残页组件翻转为已译并烙上按错误次数结算的经验。
 * 结果以 {@link SettlementResultPayload} 回执客户端。
 */
public final class PuzzleSettlement {

    public static final String REASON_MATERIALS = "gui.tiangongkaiwu.settle.fail_materials";
    public static final String REASON_STATE = "gui.tiangongkaiwu.settle.fail_state";

    private PuzzleSettlement() {
    }

    public static void handle(ServerPlayer player, PuzzleDonePayload payload) {
        boolean ok = settle(player, payload);
        String reason = ok ? "" : failReason(player, payload);
        PacketDistributor.sendToPlayer(player, new SettlementResultPayload(ok, reason));
    }

    private static boolean settle(ServerPlayer player, PuzzleDonePayload payload) {
        if (!(player.containerMenu instanceof HanmoTaiMenu menu)) {
            return false; // 玩家没开着翰墨台
        }
        Container c = menu.getContainer();
        ItemStack frag = c.getItem(HanmoTaiMenu.SLOT_CAN_YE);
        ResidualData data = frag.get(TiangongKaiwu.RESIDUAL.get());
        if (!frag.is(TiangongKaiwu.CAN_YE.get()) || data == null
                || data.translated() || data.entry().isBlank()) {
            return false; // 残页槽不是待译残页
        }
        Optional<Puzzle> byId = PuzzleRegistry.byId(payload.puzzleId());
        if (byId.isEmpty() || !byId.get().entry().equals(data.entry())) {
            return false; // 题目与残页承诺的条目对不上（防串题）
        }
        ItemStack ink = c.getItem(HanmoTaiMenu.SLOT_INK);
        ItemStack paper = c.getItem(HanmoTaiMenu.SLOT_PAPER);
        if (ink.isEmpty() || paper.isEmpty()) {
            return false; // 墨或纸缺
        }

        // ===== 通过：消耗墨纸，翻转残页为已译并烙经验 =====
        ink.shrink(1);
        paper.shrink(1);
        int xp = xpForWrongs(payload.wrongTotal());
        frag.set(TiangongKaiwu.RESIDUAL.get(), new ResidualData(data.entry(), true, xp));
        c.setChanged();
        menu.broadcastChanges(); // 立即同步槽位内容给客户端
        return true;
    }

    private static String failReason(ServerPlayer player, PuzzleDonePayload payload) {
        if (!(player.containerMenu instanceof HanmoTaiMenu menu)) {
            return "gui.tiangongkaiwu.settle.fail_no_menu";
        }
        Container c = menu.getContainer();
        ItemStack ink = c.getItem(HanmoTaiMenu.SLOT_INK);
        ItemStack paper = c.getItem(HanmoTaiMenu.SLOT_PAPER);
        ItemStack frag = c.getItem(HanmoTaiMenu.SLOT_CAN_YE);
        boolean materialsMissing = ink.isEmpty() || paper.isEmpty() || frag.isEmpty();
        return materialsMissing ? REASON_MATERIALS : REASON_STATE;
    }

    /**
     * 经验档位（按三句累计判错次数，未逐句确认的草案规则，可后续调整）：
     * 0 错（一遍过）= 30xp；1-2 错 = 20xp；3-5 错 = 12xp；6 错及以上 = 5xp。
     */
    public static int xpForWrongs(int wrongTotal) {
        if (wrongTotal <= 0) {
            return 30;
        }
        if (wrongTotal <= 2) {
            return 20;
        }
        if (wrongTotal <= 5) {
            return 12;
        }
        return 5;
    }
}
