package com.example.tiangongkaiwu.client.render;

import com.example.tiangongkaiwu.block.PowerNetwork;
import com.example.tiangongkaiwu.block.ShaftBlock;
import com.example.tiangongkaiwu.block.entity.ShaftBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * 传动杆渲染器：通着劲的杆绕自身轴旋转（D13 恒速，转速与筒车轮一致）。
 *
 * <p>红线规则（2026-10-03 拍板）：杆上铜箍的红度 = 所在网络的劲占比——
 * 劲越多越红，劲少接近原木色。实现 = 顶点色 G/B 按比例衰减（红移），
 * 基准 24 劲（两台筒车）即满红。
 */
public class ShaftRenderer implements BlockEntityRenderer<ShaftBlockEntity> {

    /** 最红时 G/B 的保留比例（0.55 → 明显红移但不糊死贴图）。 */
    public static final float MAX_REDSHIFT = 0.55F;

    public ShaftRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ShaftBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        var level = be.getLevel();
        if (level == null) {
            return;
        }

        int power = state.getValue(ShaftBlock.POWER);
        float angle = 0.0F;
        if (power > 0) {
            angle = (level.getGameTime() % 720720L + partialTick) * PowerNetwork.ROTATION_DEG_PER_TICK;
        }

        // 红度（2026-10-03 定稿）：剩余劲 / 总产劲 的占比——网络越有余越红，被机器吃满就退回木色
        float ratio = Mth.clamp(be.surplusRatio, 0.0F, 1.0F);
        float redBoost = 1.0F - (1.0F - MAX_REDSHIFT) * ratio;

        Direction.Axis axis = state.getValue(ShaftBlock.AXIS);
        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        switch (axis) {
            case X -> pose.mulPose(Axis.XP.rotationDegrees(angle));
            case Y -> pose.mulPose(Axis.YP.rotationDegrees(angle));
            case Z -> pose.mulPose(Axis.ZP.rotationDegrees(angle));
        }
        pose.translate(-0.5D, -0.5D, -0.5D);

        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModelShaper().getBlockModel(state);
        ModelBlockRenderer mbr = dispatcher.getModelRenderer();
        VertexConsumer vc = power > 0
                ? new RedTintedBuffer(buffer.getBuffer(RenderType.cutout()), redBoost)
                : buffer.getBuffer(RenderType.cutout());
        mbr.tesselateWithoutAO(level, model, state, be.getBlockPos(), pose,
                vc, false,
                RandomSource.create(), state.getSeed(be.getBlockPos()), overlay,
                ModelData.EMPTY, RenderType.cutout());

        pose.popPose();
    }

    /**
     * 顶点色红移代理：只压 G/B，R 保持——贴图整体向红偏移。
     * 只覆写 VertexConsumer 的 6 个抽象方法，其余 default 全部透传。
     */
    private record RedTintedBuffer(VertexConsumer inner, float boost) implements VertexConsumer {

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this.inner.addVertex(x, y, z);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this.inner.setColor(r, (int) (g * this.boost), (int) (b * this.boost), a);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this.inner.setUv(u, v);
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this.inner.setUv1(u, v);
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this.inner.setUv2(u, v);
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this.inner.setNormal(x, y, z);
        }
    }
}
