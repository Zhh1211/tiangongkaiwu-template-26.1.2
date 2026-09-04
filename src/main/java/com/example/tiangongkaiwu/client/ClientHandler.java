package com.example.tiangongkaiwu.client;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.client.gui.HanmoTaiScreen;
import com.example.tiangongkaiwu.menu.HanmoTaiMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = TiangongKaiwu.MODID, value = Dist.CLIENT)
public class ClientHandler {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(HanmoTaiMenu.HANMO_TAI_MENU.get(), HanmoTaiScreen::new);
    }
}