package com.example.tiangongkaiwu.client;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.client.gui.HanmoTaiScreen;
import com.example.tiangongkaiwu.client.render.JianRenderer;
import com.example.tiangongkaiwu.client.render.MechanismRenderer;
import com.example.tiangongkaiwu.client.render.ShaftRenderer;
import com.example.tiangongkaiwu.client.render.TongCheRenderer;
import com.example.tiangongkaiwu.menu.HanmoTaiMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = TiangongKaiwu.MODID, value = Dist.CLIENT)
public class ClientHandler {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(HanmoTaiMenu.HANMO_TAI_MENU.get(), HanmoTaiScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TiangongKaiwu.TONG_CHE_BE_TYPE.get(), TongCheRenderer::new);
        event.registerBlockEntityRenderer(TiangongKaiwu.JIAN_BE_TYPE.get(), JianRenderer::new);
        event.registerBlockEntityRenderer(TiangongKaiwu.SHAFT_BE_TYPE.get(), ShaftRenderer::new);
        event.registerBlockEntityRenderer(TiangongKaiwu.MECHANISM_BE_TYPE.get(), MechanismRenderer::new);
    }

    /** 包壳轴：独立烘焙套壳模型（BER 里按 AXIS 转向取用）。 */
    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ShaftRenderer.CASING_MODEL);
    }
}