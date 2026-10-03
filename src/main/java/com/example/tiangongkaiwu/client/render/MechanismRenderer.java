package com.example.tiangongkaiwu.client.render;

import com.example.tiangongkaiwu.block.DuiBlock;
import com.example.tiangongkaiwu.block.MechanismBlock;
import com.example.tiangongkaiwu.block.entity.MechanismBlockEntity;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;

/**
 * 机构方块渲染器（D15）。
 *
 * <p>SLIDER（碓杵）：从核心正上方的杵位画一根吊在梁下的杵——
 * 核心 ACTIVE 时按世界时间上下舂（D13 恒速，phase 取模防浮点漂移），静止时冻在低位。
 * 杵体细长，越出本格向下探进臼口（BER 不受格界限制，shouldRenderOffScreen 兜住邻格视角）。
 *
 * <p>PIVOT / ROTOR：后续批次（桔槔/辘轳）实现，届时按 BE 记录的原方块材质采样渲染。
 */
public class MechanismRenderer implements BlockEntityRenderer<MechanismBlockEntity> {

    /** 舂击周期：24 tick（1.2 秒一舂，与碓约 1 秒一份的出料节奏同量级）。 */
    private static final long PERIOD_TICKS = 24L;
    /** 杵的抬升幅度（像素）。 */
    private static final float LIFT_PX = 6.0F;
    /** 杵贴图（沿用碓的杵：木身钢箍）。 */
    private static final ResourceLocation PESTLE_TEX =
            ResourceLocation.fromNamespaceAndPath("tiangongkaiwu", "block/dui_pestle");

    public MechanismRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MechanismBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (state.getValue(MechanismBlock.SEMANTIC) != MechanismBlock.Semantic.SLIDER) {
            return;
        }
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        BlockPos core = be.getBlockPos().below();
        BlockState coreState = level.getBlockState(core);
        if (!(coreState.getBlock() instanceof DuiBlock) || !coreState.getValue(DuiBlock.FORMED)) {
            return;
        }

        float lift = 0.0F;
        if (coreState.getValue(DuiBlock.ACTIVE)) {
            long t = level.getGameTime() % PERIOD_TICKS;
            float phase = (t + partialTick) / PERIOD_TICKS * Mth.TWO_PI;
            lift = (float) Math.max(0.0D, Math.sin(phase)) * LIFT_PX;
        }
        int blockLight = light;

        poseStack.pushPose();
        poseStack.translate(0.0D, lift / 16.0D, 0.0D);

        VertexConsumer vc = buffer.getBuffer(RenderType.cutout());
        Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        Material atlas = new Material(InventoryMenu.BLOCK_ATLAS, PESTLE_TEX);
        TextureAtlasSprite sprite = atlas.sprite();

        // 杵体：细杆（吊到梁底）+ 杵头（探进臼口）；lift 已由整体平移带起
        box(vc, pose, mat, sprite, blockLight, 7, -8, 7, 9, 16, 9);    // 杆
        box(vc, pose, mat, sprite, blockLight, 5, -12, 5, 11, -8, 11); // 头

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
        return true; // 杵体越出本格向下，相机在邻格时也要画
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
