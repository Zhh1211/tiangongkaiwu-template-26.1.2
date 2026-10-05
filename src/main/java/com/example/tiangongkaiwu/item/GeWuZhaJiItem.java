package com.example.tiangongkaiwu.item;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.block.GearBlock;
import com.example.tiangongkaiwu.block.GearBoxBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 格物札记（D17 可选 QoL 支线）：格物致知的载体——**推究实物而致知**。
 *
 * <p>tooltip 列出全部进行中研究的「三件套」：文言谜面（原理）+ 白话目标（效果）+ 材料清单。
 * 玩家照谜面在世界里摆实物，**手持札记右键结构中的任意部件**判定：
 * <ul>
 * <li>`structure` 型：结构匹配（如合轮 = 四牙轮围成一圈、轴平行地面且都指向中心）；</li>
 * <li>`items` 型：贡物料（框架预留，本批未接研究）。</li>
 * </ul>
 * 致知 = grant 对应 advancement（解锁齿轮箱配方）+ toast；札记用完即毁（残页同款）。
 *
 * <p>研究注册表本批为静态清单（{@link #RESEARCHES}）；攒到 3+ 再迁 JSON 数据管线。
 */
public class GeWuZhaJiItem extends Item {

    /** 一条格物研究。 */
    public record Research(String id, String riddleKey, String goalKey, String materialsKey,
                           Matcher matcher, String rewardAdvancement) {
    }

    /** 结构判定器：被点的部件位 + 世界 → 是否成局。 */
    public interface Matcher {
        boolean matches(ServerLevel level, BlockPos clicked);
    }

    /** 进行中的研究清单（静态注册；攒到 3+ 迁 JSON）。 */
    public static final List<Research> RESEARCHES = List.of(
            new Research("he_lun",
                    "ge_wu.he_lun.riddle", "ge_wu.he_lun.goal", "ge_wu.he_lun.materials",
                    GeWuZhaJiItem::matchHeLun,
                    "tech/he_lun")
    );

    public GeWuZhaJiItem(Properties properties) {
        super(properties);
    }

    // ==================== tooltip：研究三件套 ====================

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltips, TooltipFlag flag) {
        for (Research r : RESEARCHES) {
            tooltips.add(Component.translatable(r.riddleKey()).withStyle(s -> s.withItalic(true).withColor(0x776655)));
            tooltips.add(Component.translatable(r.goalKey()).withStyle(s -> s.withColor(0x555555)));
            tooltips.add(Component.translatable(r.materialsKey()).withStyle(s -> s.withColor(0x556677)));
            tooltips.add(Component.empty());
        }
        if (!RESEARCHES.isEmpty()) {
            tooltips.remove(tooltips.size() - 1); // 去掉末尾空行
        }
    }

    // ==================== 判定：右键结构中的部件 ====================

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();

        if (!(state.getBlock() instanceof GearBlock)) {
            return InteractionResult.PASS; // 只对结构件判定
        }
        if (level instanceof ServerLevel server) {
            for (Research r : RESEARCHES) {
                if (!r.matcher().matches(server, pos)) {
                    continue;
                }
                if (grant(server, (ServerPlayer) player, r)) {
                    context.getItemInHand().shrink(1); // 札记用完即毁（残页同款）
                } else if (player != null) {
                    player.displayClientMessage(Component.translatable("ge_wu.already_known"), true);
                }
                break;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 致知：grant 奖励 advancement（advancement rewards 挂配方解锁）。 */
    private static boolean grant(ServerLevel level, ServerPlayer player, Research research) {
        if (player == null) {
            return false;
        }
        ResourceLocation advId = ResourceLocation.fromNamespaceAndPath(TiangongKaiwu.MODID,
                "tech/" + research.id());
        var holder = player.server.getAdvancements().get(advId);
        if (holder == null) {
            return false;
        }
        boolean first = player.getAdvancements().getOrStartProgress(holder).isDone() == false;
        if (!first) {
            return false;
        }
        player.getAdvancements().award(holder, "unlock");
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1, player.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
        return true;
    }

    // ==================== 合轮判定：四牙轮围成一圈，轴指中心 ====================

    /**
     * 合轮（十字齿轮箱内部结构）：四个牙轮围成一圈，**轴平行地面且都指向中心**
     * （东西两轮轴沿 X、南北两轮轴沿 Z——两两垂直，即机动的十字齿轮箱内部）。
     * 被点的牙轮可以是四者之一（围成圈会转向匹配）。
     */
    static boolean matchHeLun(ServerLevel level, BlockPos clicked) {
        // 被点的牙轮在圈上，中心必在它的四个水平方向之一
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos center = clicked.relative(d);
            if (checkCross(level, center)) {
                return true;
            }
        }
        return false;
    }

    /** 十字校验：中心四水平邻全为牙轮，且轴向指向中心（东西轴 X、南北轴 Z）；中心不得是牙轮。 */
    private static boolean checkCross(ServerLevel level, BlockPos center) {
        if (level.getBlockState(center).getBlock() instanceof GearBlock) {
            return false; // 中心是齿轮箱心脏位，不能直接摆牙轮
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = center.relative(d);
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof GearBlock)) {
                return false;
            }
            // 轴指向中心：东/西位的轮轴沿 X，南/北位的轮轴沿 Z
            if (s.getValue(GearBlock.AXIS) != d.getAxis()) {
                return false;
            }
        }
        return true;
    }
}
