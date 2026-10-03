package com.example.tiangongkaiwu.client.render;

import com.example.tiangongkaiwu.block.TongCheBlock;
import com.example.tiangongkaiwu.block.entity.TongCheBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * 筒车渲染器：让 3D 大轮「激轮使转」。
 *
 * <p>做法：方块本体走 ENTITYBLOCK_ANIMATED（静态模型不渲染），
 * 由这里把整轮烘焙模型绕轮轴旋转后用 ModelBlockRenderer 直接 tesselate。
 * 注意不能用 {@code renderSingleBlock}——它对 ENTITYBLOCK_ANIMATED 形状直接跳过（一片透明）。
 *
 * <p>转速 = 每游戏刻 2°（约 9 秒一圈），只有 ACTIVE（水够、没插栓）时推进角度——
 * 栓木碍止时轮子停住。
 */
public class TongCheRenderer implements BlockEntityRenderer<TongCheBlockEntity> {

    /** 转速统一取自 PowerNetwork（D13：全设备同速）。 */
    public static final float DEG_PER_TICK = com.example.tiangongkaiwu.block.PowerNetwork.ROTATION_DEG_PER_TICK;

    /** BER 提供者会传 Context 进来（注册接口约定），本渲染器用不到，收下即可。 */
    public TongCheRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TongCheBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        var level = be.getLevel();
        if (level == null) {
            return;
        }

        // 转角 = 世界时间同源（2026-10-03 拍板）：所有轮子从同一个钟算角度，
        // 天然全服同步——不再用各自 BE 累加（会各转各的、读档还归零）。
        // 一圈 = 180 tick（360° ÷ 2°/tick），对 180 取模防大数浮点误差。
        boolean active = state.getValue(TongCheBlock.ACTIVE) && !state.getValue(TongCheBlock.PLUGGED);
        float now = (level.getGameTime() % 180L + partialTick) * DEG_PER_TICK;
        float angle;
        if (active) {
            be.lastActive = true;
            angle = now;
        } else {
            // 「转→停」瞬间抓拍当前角，之后冻在原地（断水的轮子停在手上，不跳回 0 度）
            if (be.lastActive) {
                be.frozenAngle = now;
                be.lastActive = false;
            }
            angle = be.frozenAngle;
        }

        Direction.Axis plane = state.getValue(TongCheBlock.PLANE);
        BlockPos pos = be.getBlockPos();

        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        if (plane == Direction.Axis.Z) {
            pose.mulPose(Axis.ZP.rotationDegrees(angle));
        } else {
            pose.mulPose(Axis.XP.rotationDegrees(angle));
        }
        pose.translate(-0.5D, -0.5D, -0.5D);

        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModelShaper().getBlockModel(state);
        ModelBlockRenderer mbr = dispatcher.getModelRenderer();
        mbr.tesselateWithoutAO(level, model, state, pos, pose,
                buffer.getBuffer(RenderType.cutout()), false,
                RandomSource.create(), state.getSeed(pos), overlay,
                ModelData.EMPTY, RenderType.cutout());

        pose.popPose();
    }
}
