package com.example.tiangongkaiwu.menu;

import com.example.tiangongkaiwu.TiangongKaiwu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class HanmoTaiMenu extends AbstractContainerMenu {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, TiangongKaiwu.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<HanmoTaiMenu>> HANMO_TAI_MENU =
            MENUS.register("hanmo_tai_menu", () -> new MenuType<HanmoTaiMenu>(HanmoTaiMenu::new));

    public HanmoTaiMenu(int containerId, Inventory playerInventory) {
        super(HANMO_TAI_MENU.get(), containerId);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}