package com.example.tiangongkaiwu.client.render;

import com.example.tiangongkaiwu.block.JianBlock;
import com.example.tiangongkaiwu.block.entity.JianBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;

/**
 * 梘内流体的动态渲染（D12）：装什么流体就取什么流体的静水贴图与染色——
 * 原版水走群系色、熔岩自带色、任意模组流体全兼容，资源包自动跟随。
 *
 * <p>水柱高度按 16 档有效水位（LEVEL×SUB）线性映射到槽内净高，
 * 画一个顶面 + 四侧的盒体；贴图从方块图集取 sprite，UV 铺满每面。
 */
public class JianRenderer implements BlockEntityRenderer<JianBlockEntity> {

    /** 槽内净高：底板顶 2px，水面最高到 6.5px（墙 7px 留 0.5 边）。 */
    private static final float WATER_BOTTOM = 2.0F / 16.0F;
    private static final float WATER_TOP_MAX = 6.5F / 16.0F;
    /** 面内边距：水柱四边缩进 1px，让槽壁包住水。 */
    private static final float INSET = 1.0F / 16.0F;

    public JianRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(JianBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof JianBlock)) {
            return;
        }
        // 液量即真相（D16 连通器模型）：填充率 = 段内 mB / 段容量
        FluidStack fluid = be.getFluid();
        int mb = fluid == null || fluid.isEmpty() ? 0 : fluid.getAmount();
        if (mb <= 0) {
            return;
        }
        float fill = Math.min(1.0F, mb / (float) JianBlockEntity.SEGMENT_CAPACITY);

        // 流体贴图与染色（D12）：BE 里是什么流体就用什么的
        if (fluid == null || fluid.isEmpty()) {
            fluid = new FluidStack(Fluids.WATER, 1);
        }
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
        ResourceLocation stillTex = ext.getStillTexture(fluid);
        Material material = new Material(TextureAtlas.LOCATION_BLOCKS, stillTex);
        TextureAtlasSprite sprite = material.sprite();
        int tint = ext.getTintColor(fluid);
        float r;
        float g;
        float b;
        if (tint == -1) {
            // 无固有染色（原版水）→ 群系水色
            int biomeTint = be.getLevel() != null
                    ? be.getLevel().getBiome(be.getBlockPos()).value().getWaterColor()
                    : 0x3F76E4;
            r = (biomeTint >> 16 & 0xFF) / 255.0F;
            g = (biomeTint >> 8 & 0xFF) / 255.0F;
            b = (biomeTint & 0xFF) / 255.0F;
        } else {
            r = (tint >> 16 & 0xFF) / 255.0F;
            g = (tint >> 8 & 0xFF) / 255.0F;
            b = (tint & 0xFF) / 255.0F;
        }

        float y0 = WATER_BOTTOM;
        float y1 = Mth.lerp(fill, WATER_BOTTOM, WATER_TOP_MAX);
        float x0 = INSET;
        float x1 = 1.0F - INSET;
        float z0 = INSET;
        float z1 = 1.0F - INSET;

        int light = be.getLevel() != null
                ? net.minecraft.client.renderer.LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos())
                : net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;

        VertexConsumer vc = buffers.getBuffer(RenderType.translucent());
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();

        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        // 顶面（朝上）
        quad(vc, pose, mat,
                x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1,
                u0, v0, u1, v0, u1, v1, u0, v1, r, g, b, light);
        // 底面（朝下，浅水时从上往下看得见槽底水色）
        quad(vc, pose, mat,
                x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0,
                u0, v0, u0, v1, u1, v1, u1, v0, r, g, b, light);
        // 北面（z0）
        quad(vc, pose, mat,
                x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0,
                u0, v1, u1, v1, u1, v0, u0, v0, r, g, b, light);
        // 南面（z1）
        quad(vc, pose, mat,
                x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1,
                u0, v1, u1, v1, u1, v0, u0, v0, r, g, b, light);
        // 西面（x0）
        quad(vc, pose, mat,
                x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1,
                u0, v1, u1, v1, u1, v0, u0, v0, r, g, b, light);
        // 东面（x1）
        quad(vc, pose, mat,
                x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0,
                u0, v1, u1, v1, u1, v0, u0, v0, r, g, b, light);
    }

    /** 按已给定的 4 角坐标与 UV 画一个四边形。 */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float uA, float vA, float uB, float vB, float uC, float vC, float uD, float vD,
                             float r, float g, float b, int light) {
        vertex(vc, pose, mat, ax, ay, az, uA, vA, r, g, b, light);
        vertex(vc, pose, mat, bx, by, bz, uB, vB, r, g, b, light);
        vertex(vc, pose, mat, cx, cy, cz, uC, vC, r, g, b, light);
        vertex(vc, pose, mat, dx, dy, dz, uD, vD, r, g, b, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat,
                               float x, float y, float z, float u, float v,
                               float r, float g, float b, int light) {
        vc.addVertex(mat, x, y, z)
                .setColor(r, g, b, 1.0F)
                .setUv(u, v)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
