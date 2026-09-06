package com.example.tiangongkaiwu.block.entity;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.menu.HanmoTaiMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 翰墨台的方块实体：三格材料容器（残页 / 墨水 / 纸）。
 *
 * 物品存放在方块内而不是玩家手里：关闭界面不归还、随区块存档、
 * 重进世界仍在；方块被破坏时由 {@code HanmoTaiBlock.onRemove} 掉落内容。
 */
public class HanmoTaiBlockEntity extends BlockEntity implements Container, MenuProvider {

    private final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);

    public HanmoTaiBlockEntity(BlockPos pos, BlockState state) {
        super(TiangongKaiwu.HANMO_TAI_BE_TYPE.get(), pos, state);
    }

    // ============================================================
    // 持久化：三格材料存入方块 NBT
    // ============================================================
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, this.items, registries);
    }

    // ============================================================
    // Container：委托给内部物品列表
    // ============================================================
    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        if (index < 0 || index >= this.items.size()) {
            return ItemStack.EMPTY;
        }
        return this.items.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int amount) {
        ItemStack removed = ContainerHelper.removeItem(this.items, index, amount);
        if (!removed.isEmpty()) {
            this.setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        if (index < 0 || index >= this.items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = this.items.get(index);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        this.items.set(index, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (index < 0 || index >= this.items.size()) {
            return;
        }
        this.items.set(index, stack);
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        Level level = this.level;
        if (level == null) {
            return false;
        }
        return player.distanceToSqr(
                this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 0.5D,
                this.worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clearContent() {
        // 逐格清空而非 list.clear()，避免后续 setItem(index) 越界
        for (int i = 0; i < this.items.size(); i++) {
            this.items.set(i, ItemStack.EMPTY);
        }
        this.setChanged();
    }

    @Override
    public void setChanged() {
        super.setChanged();
    }

    // ============================================================
    // MenuProvider：右键方块时由方块调用本方法打开菜单
    // ============================================================
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.tiangongkaiwu.hanmo_tai");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new HanmoTaiMenu(containerId, playerInventory, this,
                ContainerLevelAccess.create(this.level, this.worldPosition));
    }
}
