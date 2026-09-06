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
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.function.Predicate;

public class HanmoTaiMenu extends AbstractContainerMenu {

    // 注册已在 TiangongKaiwu 主类的静态字段 HANMO_TAI_MENU 完成（构造期注册，时机正确），
    // 此处仅复用同一 DeferredHolder，避免在本类 <clinit> 里重复注册导致时机错误。
    public static final DeferredHolder<MenuType<?>, MenuType<HanmoTaiMenu>> HANMO_TAI_MENU =
            TiangongKaiwu.HANMO_TAI_MENU;

    /** 三个输入槽的下标 */
    public static final int SLOT_CAN_YE = 0;   // 残页
    public static final int SLOT_INK = 1;      // 墨水
    public static final int SLOT_PAPER = 2;    // 纸
    public static final int INPUT_SLOT_COUNT = 3;

    // 输入槽左上角（左侧竖排）。GUI 贴图定稿后按新布局调整。
    private static final int INPUT_X = 12;
    private static final int INPUT_Y = 17;
    private static final int INPUT_GAP = 18;

    private final Container container;
    private final ContainerLevelAccess access;

    /** 客户端构造（注册工厂经 IMenuTypeExtension 调用）：无方块位置，用空容器占位 + NULL 访问。 */
    public HanmoTaiMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(INPUT_SLOT_COUNT), ContainerLevelAccess.NULL);
    }

    /**
     * 服务端构造：传入方块实体的真实容器与方块位置。
     * 容器直接指向 HanmoTaiBlockEntity，材料留在方块内，
     * 关闭界面不归还、不丢失。
     */
    public HanmoTaiMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access) {
        super(HANMO_TAI_MENU.get(), containerId);
        this.access = access;
        this.container = container;

        // 左侧三个专用槽：残页 / 墨水 / 纸，各槽只收自己的材料
        this.addSlot(new MaterialSlot(this.container, SLOT_CAN_YE, INPUT_X, INPUT_Y, HanmoTaiMenu::isFragment));
        this.addSlot(new MaterialSlot(this.container, SLOT_INK, INPUT_X, INPUT_Y + INPUT_GAP, HanmoTaiMenu::isInk));
        this.addSlot(new MaterialSlot(this.container, SLOT_PAPER, INPUT_X, INPUT_Y + INPUT_GAP * 2, HanmoTaiMenu::isPaper));

        // 玩家背包 3x9（索引 3..29）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // 快捷栏（索引 30..38）
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }

    // ============================================================
    // 材料白名单：原版与自制材料均可放入
    // ============================================================
    private static boolean isFragment(ItemStack stack) {
        return stack.is(TiangongKaiwu.CAN_YE.get());
    }

    private static boolean isInk(ItemStack stack) {
        return stack.is(Items.INK_SAC) || stack.is(TiangongKaiwu.SONGYAN_MO.get());
    }

    private static boolean isPaper(ItemStack stack) {
        return stack.is(Items.PAPER) || stack.is(TiangongKaiwu.XUAN_ZHI.get());
    }

    /** 三样材料是否都已就位。 */
    public boolean hasAllMaterials() {
        return isFragment(this.container.getItem(SLOT_CAN_YE))
                && isInk(this.container.getItem(SLOT_INK))
                && isPaper(this.container.getItem(SLOT_PAPER));
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
            // 玩家背包/快捷栏 → 输入槽（能放进去的槽自然会被选中，放不进的会被跳过）
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

    /** 材料容器（服务端为方块实体的容器，客户端为空占位）。供“译”逻辑读取。 */
    public Container getContainer() {
        return this.container;
    }

    /** 只接受指定材料的槽位。 */
    private static class MaterialSlot extends Slot {
        private final Predicate<ItemStack> filter;

        MaterialSlot(Container container, int index, int x, int y, Predicate<ItemStack> filter) {
            super(container, index, x, y);
            this.filter = filter;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.filter.test(stack);
        }
    }
}
