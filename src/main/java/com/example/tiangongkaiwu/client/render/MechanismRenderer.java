package com.example.tiangongkaiwu.client.render;

import com.example.tiangongkaiwu.block.DuiBlock;
import com.example.tiangongkaiwu.block.MechanismBlock;
import com.example.tiangongkaiwu.block.WellLiftBlock;
import com.example.tiangongkaiwu.block.entity.MechanismBlockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import org.joml.Matrix4f;

/**
 * 机构方块渲染器（D15）。
 *
 * <p>SLIDER（碓杵）：从核心正上方的杵位画一根吊在梁下的杵——
 * 核心 ACTIVE 时按世界时间上下舂（D13 恒速，phase 取模防浮点漂移），静止时冻在低位。
 * 杵体细长，越出本格向下探进臼口（BER 不受格界限制，shouldRenderOffScreen 兜住邻格视角）。
 *
 * <p>PIVOT（桔槔横杆）：画一根架在支点上的长杆，ACTIVE 时绕支点小幅摆动（配重一压一翘），
 * 杆一端吊桶随摆起落；ROTOR（辘轳绞轮）：画横置滚筒加摇柄，ACTIVE（有人摇）时恒速自转。
 * 两者都读正下方核心（WellLiftBlock）的 FACING 与 ACTIVE，木料贴图采样原版原木。
 */
public class MechanismRenderer implements BlockEntityRenderer<MechanismBlockEntity> {

    /** 舂击周期：24 tick（1.2 秒一舂，与碓约 1 秒一份的出料节奏同量级）。 */
    private static final long PERIOD_TICKS = 24L;
    /** 桔槔摆动 / 辘轳自转周期：32 tick（约 1.6 秒一摆/一圈，恒速不随快慢变）。 */
    private static final long SWING_PERIOD_TICKS = 32L;
    /** 杵的抬升幅度（像素）。 */
    private static final float LIFT_PX = 6.0F;
    /** 桔槔摆幅（弧度，约 9°）。 */
    private static final float SWING_RAD = 0.16F;
    /** 杵贴图（沿用碓的杵：木身钢箍）。 */
    private static final ResourceLocation PESTLE_TEX =
            ResourceLocation.fromNamespaceAndPath("tiangongkaiwu", "block/dui_pestle");
    /** 桔槔杆 / 辘轳轮：原版橡木贴图（明暗由纹理自带）。 */
    private static final ResourceLocation LOG_TEX =
            ResourceLocation.fromNamespaceAndPath("minecraft", "block/oak_log");
    private static final ResourceLocation PLANKS_TEX =
            ResourceLocation.fromNamespaceAndPath("minecraft", "block/oak_planks");

    public MechanismRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MechanismBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        BlockPos core = be.getBlockPos().below();
        BlockState coreState = level.getBlockState(core);
        if (!MechanismBlock.isFormedCore(coreState)) {
            return;
        }

        switch (state.getValue(MechanismBlock.SEMANTIC)) {
            case SLIDER -> renderSlider(coreState, level, partialTick, poseStack, buffer, light);
            case PIVOT -> renderPivot(coreState, level, partialTick, poseStack, buffer, light);
            case ROTOR -> renderRotor(coreState, level, partialTick, poseStack, buffer, light);
        }
    }

    // ==================== SLIDER（碓杵） ====================

    private void renderSlider(BlockState coreState, Level level, float partialTick,
                              PoseStack poseStack, MultiBufferSource buffer, int light) {
        float lift = 0.0F;
        if (coreState.getValue(DuiBlock.ACTIVE)) {
            long t = level.getGameTime() % PERIOD_TICKS;
            float phase = (t + partialTick) / PERIOD_TICKS * Mth.TWO_PI;
            lift = (float) Math.max(0.0D, Math.sin(phase)) * LIFT_PX;
        }

        poseStack.pushPose();
        poseStack.translate(0.0D, lift / 16.0D, 0.0D);

        VertexConsumer vc = buffer.getBuffer(RenderType.cutout());
        Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        TextureAtlasSprite sprite = new Material(InventoryMenu.BLOCK_ATLAS, PESTLE_TEX).sprite();

        // 杵体：细杆（吊到梁底）+ 杵头（探进臼口）；lift 已由整体平移带起
        box(vc, pose, mat, sprite, light, 7, -8, 7, 9, 16, 9);    // 杆
        box(vc, pose, mat, sprite, light, 5, -12, 5, 11, -8, 11); // 头

        poseStack.popPose();
    }

    // ==================== PIVOT（桔槔横杆：绕支点摆） ====================

    private void renderPivot(BlockState coreState, Level level, float partialTick,
                             PoseStack poseStack, MultiBufferSource buffer, int light) {
        Direction facing = coreState.getValue(WellLiftBlock.FACING);
        boolean active = coreState.getValue(WellLiftBlock.ACTIVE);

        float angle = 0.0F;
        if (active) {
            long t = level.getGameTime() % SWING_PERIOD_TICKS;
            float phase = (t + partialTick) / SWING_PERIOD_TICKS * Mth.TWO_PI;
            angle = (float) Math.sin(phase) * SWING_RAD;
        }

        VertexConsumer vc = buffer.getBuffer(RenderType.cutout());
        TextureAtlasSprite log = new Material(InventoryMenu.BLOCK_ATLAS, LOG_TEX).sprite();
        TextureAtlasSprite planks = new Material(InventoryMenu.BLOCK_ATLAS, PLANKS_TEX).sprite();

        // 支点：机构格中部。摆动绕面向轴（杆垂直于面向方向架设，桶端下压、坠石端上翘）。
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.15D, 0.5D);
        poseStack.mulPose(swingAround(facing).rotation(angle));
        poseStack.translate(-0.5D, -0.15D, -0.5D);

        Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();

        boolean alongX = facing.getAxis() == Direction.Axis.Z; // 面向北南 → 杆沿东西(X)
        if (alongX) {
            // 长杆：支点不在正中——长端吊水桶、短端坠石（桔槔的本相）
            box(vc, pose, mat, log, light, -7, 5.5F, 7.25F, 19, 7.5F, 8.75F);      // 杆
            box(vc, pose, mat, log, light, 14.5F, 7.5F, 7.6F, 15.5F, 19, 8.4F);    // 长端吊绳
            box(vc, pose, mat, planks, light, 12.5F, 19, 6.6F, 17.5F, 24, 9.4F);   // 水桶
            box(vc, pose, mat, log, light, -9.5F, 7.5F, 6.6F, -3.5F, 10.5F, 9.4F); // 短端坠石
        } else {
            box(vc, pose, mat, log, light, 7.25F, 5.5F, -7, 8.75F, 7.5F, 19);
            box(vc, pose, mat, log, light, 7.6F, 7.5F, 14.5F, 8.4F, 19, 15.5F);
            box(vc, pose, mat, planks, light, 6.6F, 19, 12.5F, 9.4F, 24, 17.5F);
            box(vc, pose, mat, log, light, 6.6F, 7.5F, -9.5F, 9.4F, 10.5F, -3.5F);
        }

        poseStack.popPose();
    }

    /** 摆动轴：面向南北（杆沿 X）绕 Z 摆；面向东西（杆沿 Z）绕 X 摆。 */
    private static Axis swingAround(Direction facing) {
        return facing.getAxis() == Direction.Axis.Z ? Axis.ZP : Axis.XP;
    }

    // ==================== ROTOR（辘轳绞轮：绕水平轴自转） ====================

    private void renderRotor(BlockState coreState, Level level, float partialTick,
                             PoseStack poseStack, MultiBufferSource buffer, int light) {
        Direction facing = coreState.getValue(WellLiftBlock.FACING);
        boolean active = coreState.getValue(WellLiftBlock.ACTIVE);

        float spin = 0.0F;
        if (active) {
            spin = (level.getGameTime() % SWING_PERIOD_TICKS + partialTick)
                    / SWING_PERIOD_TICKS * Mth.TWO_PI;
        }

        VertexConsumer vc = buffer.getBuffer(RenderType.cutout());
        TextureAtlasSprite log = new Material(InventoryMenu.BLOCK_ATLAS, LOG_TEX).sprite();

        // 滚筒沿左右方向（垂直面向），绕自身水平轴恒速转（有人摇才转）
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(swingAround(facing).rotation(-spin));
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();

        boolean alongX = facing.getAxis() == Direction.Axis.Z;
        if (alongX) {
            box(vc, pose, mat, log, light, 1, 6.5F, 6.5F, 15, 11.5F, 11.5F);          // 滚筒
            box(vc, pose, mat, log, light, 15, 7.5F, 7.5F, 19, 9.5F, 9.5F);           // 摇柄臂
            box(vc, pose, mat, log, light, 17.5F, 7.5F, 5.5F, 19.5F, 9.5F, 11.5F);    // 摇柄把
        } else {
            box(vc, pose, mat, log, light, 6.5F, 6.5F, 1, 11.5F, 11.5F, 15);
            box(vc, pose, mat, log, light, 7.5F, 7.5F, 15, 9.5F, 9.5F, 19);
            box(vc, pose, mat, log, light, 5.5F, 7.5F, 17.5F, 11.5F, 9.5F, 19.5F);
        }

        poseStack.popPose();
    }

    /** 画一个 6 面盒（像素坐标 /16；UV 按坐标比例包裹映射，负坐标也取得到贴图）。 */
    private static void box(VertexConsumer vc, Pose pose, Matrix4f mat, TextureAtlasSprite sprite, int light,
                            float x0, float y0, float z0, float x1, float y1, float z1) {
        // north(-z)：从外看 CCW
        face(vc, pose, mat, sprite, light,
                x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0,
                x1, 16 - y0, x0, 16 - y0, x0, 16 - y1, x1, 16 - y1);
        // south(+z)
        face(vc, pose, mat, sprite, light,
                x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1,
                x0, 16 - y0, x1, 16 - y0, x1, 16 - y1, x0, 16 - y1);
        // west(-x)
        face(vc, pose, mat, sprite, light,
                x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0,
                z0, 16 - y0, z1, 16 - y0, z1, 16 - y1, z0, 16 - y1);
        // east(+x)
        face(vc, pose, mat, sprite, light,
                x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1,
                z1, 16 - y0, z0, 16 - y0, z0, 16 - y1, z1, 16 - y1);
        // down(-y)
        face(vc, pose, mat, sprite, light,
                x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
                x0, z0, x1, z0, x1, z1, x0, z1);
        // up(+y)
        face(vc, pose, mat, sprite, light,
                x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0,
                x0, z0, x0, z1, x1, z1, x1, z0);
    }

    /** 画一个四边形面：pos 12 分量 + uv 8 分量（px）。 */
    private static void face(VertexConsumer vc, Pose pose, Matrix4f mat, TextureAtlasSprite sprite, int light,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float ua, float va, float ub, float vb, float uc, float vc2, float ud, float vd) {
        vertex(vc, pose, mat, sprite, light, ax, ay, az, ua, va);
        vertex(vc, pose, mat, sprite, light, bx, by, bz, ub, vb);
        vertex(vc, pose, mat, sprite, light, cx, cy, cz, uc, vc2);
        vertex(vc, pose, mat, sprite, light, dx, dy, dz, ud, vd);
    }

    private static void vertex(VertexConsumer vc, Pose pose, Matrix4f mat, TextureAtlasSprite sprite, int light,
                               float x, float y, float z, float u, float v) {
        float uu = Mth.lerp(wrap(u / 16.0F), sprite.getU0(), sprite.getU1());
        float vv = Mth.lerp(wrap(v / 16.0F), sprite.getV0(), sprite.getV1());
        vc.addVertex(mat, x / 16.0F, y / 16.0F, z / 16.0F)
                .setColor(1.0F, 1.0F, 1.0F, 1.0F)
                .setUv(uu, vv)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /** 把越界 UV 包回 0..1。 */
    private static float wrap(float f) {
        return f - Mth.floor(f);
    }

    @Override
    public boolean shouldRenderOffScreen(MechanismBlockEntity be) {
        return true; // 杵体/长杆越出本格，相机在邻格时也要画
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public boolean shouldRender(MechanismBlockEntity be, Vec3 camera) {
        return true;
    }
}
