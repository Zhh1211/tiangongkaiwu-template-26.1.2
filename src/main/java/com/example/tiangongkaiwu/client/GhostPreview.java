package com.example.tiangongkaiwu.client;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.block.DuiBlock;
import com.example.tiangongkaiwu.multiblock.MultiBlockTemplates;
import com.example.tiangongkaiwu.multiblock.StructureTemplate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 幽灵预览（2026-10-04 拍板）：手持墨斗瞄准未成型机器核心 → 线框描出整个结构。
 *
 * <p>配色语义（缺件明细同源）：
 * <ul>
 * <li>白 = 木料位（缺 = 亮白实线，就位 = 暗灰细线）</li>
 * <li>黄 = 料口（漏斗）</li>
 * <li>红 = 轴位（传动杆）</li>
 * <li>绿 = 运动件（成型时自动换装，永远「不缺」）</li>
 * </ul>
 *
 * <p>实现：RenderLevelStageEvent AFTER_TRANSLUCENT_BLOCKS + RenderType.lines +
 * LevelRenderer.renderLineBox（签名已 javap 核实）——与全 mod 手写渲染路线 A 一致，不引渲染库。
 * 模板查表 {@link MultiBlockTemplates} 走 classpath，客户端同样可用；
 * 预览旋转取 {@link StructureTemplate#bestRotAt}（最接近能成的视角）。
 */
@EventBusSubscriber(modid = TiangongKaiwu.MODID, value = Dist.CLIENT)
public final class GhostPreview {

    private static final float[] WOOD = {0.92F, 0.92F, 0.92F};
    private static final float[] FEED = {0.95F, 0.80F, 0.10F};
    private static final float[] SHAFT = {0.90F, 0.20F, 0.15F};
    private static final float[] SLIDER = {0.35F, 0.90F, 0.50F};
    private static final float[] OK_DIM = {0.55F, 0.55F, 0.55F};

    private GhostPreview() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        ItemStack main = player.getMainHandItem();
        if (main.isEmpty() || main.getItem() != TiangongKaiwu.MODOU.get()) {
            return;
        }
        HitResult hit = mc.hitResult;
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            return;
        }
        BlockPos core = blockHit.getBlockPos();
        if (!(mc.level.getBlockState(core).getBlock() instanceof DuiBlock)
                || mc.level.getBlockState(core).getValue(DuiBlock.FORMED)) {
            return;
        }
        StructureTemplate template = MultiBlockTemplates.get("dui");
        if (template == null) {
            return;
        }

        // 预览方向随玩家面向（2026-10-04 反馈）：缺件并列（如空地）时取玩家朝向
        // 对应的旋转；已有摆放痕迹时仍跟随实际摆法（唯一最优旋转胜出）。
        int preferred = switch (player.getDirection()) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            default -> 3; // WEST
        };
        int rot = template.bestRotAt(mc.level, core, preferred);
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        VertexConsumer buf = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        for (StructureTemplate.Part part : template.parts()) {
            boolean ok = template.checkPart(mc.level, core, part, rot);
            float[] c = ok ? OK_DIM : colorFor(part.role());
            BlockPos p = core.offset(StructureTemplate.spin(part.pos(), rot));
            AABB box = new AABB(p).inflate(-0.002D);
            LevelRenderer.renderLineBox(pose, buf, box, c[0], c[1], c[2], ok ? 0.35F : 0.95F);
        }
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
        pose.popPose();
    }

    private static float[] colorFor(StructureTemplate.Role role) {
        return switch (role) {
            case WOOD, PIVOT, ROTOR -> WOOD;
            case SLIDER -> SLIDER;
            case FEED -> FEED;
            case SHAFT_SOCKET -> SHAFT;
        };
    }
}
