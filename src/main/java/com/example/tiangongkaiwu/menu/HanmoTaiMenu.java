package com.example.tiangongkaiwu.menu;

import com.example.tiangongkaiwu.TiangongKaiwu;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

public class HanmoTaiMenu extends AbstractContainerMenu {

    // 注册已在 TiangongKaiwu 主类的静态字段 HANMO_TAI_MENU 完成（构造期注册，时机正确），
    // 此处仅复用同一 DeferredHolder，避免在本类 <clinit> 里重复注册导致时机错误。
    public static final DeferredHolder<MenuType<?>, MenuType<HanmoTaiMenu>> HANMO_TAI_MENU =
            TiangongKaiwu.HANMO_TAI_MENU;

    /** 输入槽数量：放残页用。索引 0..2。 */
    public static final int INPUT_SLOT_COUNT = 3;

    private final Container container;
    private final ContainerLevelAccess access;

    /** 客户端构造：无方块位置，用 NULL 访问（与工作台菜单同样的做法）。 */
    public HanmoTaiMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    /** 服务端构造：传入方块位置，用于 stillValid 距离判定与关闭时归还物品。 */
    public HanmoTaiMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(HANMO_TAI_MENU.get(), containerId);
        this.access = access;
        this.container = new SimpleContainer(INPUT_SLOT_COUNT);

        // 输入槽：3 格，横向居中
        for (int i = 0; i < INPUT_SLOT_COUNT; i++) {
            this.addSlot(new Slot(this.container, i, 62 + i * 18, 35));
        }

        // 玩家背包 3x9（索引 9..35）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // 快捷栏（索引 0..8）
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (slotIndex < INPUT_SLOT_COUNT) {
            // 输入槽 → 玩家背包/快捷栏
            if (!this.moveItemStackTo(stack, INPUT_SLOT_COUNT, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 玩家背包/快捷栏 → 输入槽
            if (!this.moveItemStackTo(stack, 0, INPUT_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, TiangongKaiwu.HANMO_TAI.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // 只在服务端（真实 access）归还：客户端 access 为 NULL，execute 不会执行，避免同步问题
        this.access.execute((level, pos) -> this.clearContainer(player, this.container));
    }

    /** 输入容器，供“译”逻辑读取。 */
    public Container getContainer() {
        return this.container;
    }
}
