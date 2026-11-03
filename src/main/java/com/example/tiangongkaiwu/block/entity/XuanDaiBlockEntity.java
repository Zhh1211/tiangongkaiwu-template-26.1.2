package com.example.tiangongkaiwu.block.entity;

import com.example.tiangongkaiwu.TiangongKaiwu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/**
 * 悬袋的方块实体（物流一期 D19 §11）：袋内货物暂存（一组）。
 */
public class XuanDaiBlockEntity extends BlockEntity {

    private ItemStack buffer = ItemStack.EMPTY;

    public XuanDaiBlockEntity(BlockPos pos, BlockState state) {
        super(TiangongKaiwu.XUAN_DAI_BE_TYPE.get(), pos, state);
    }

    public ItemStack getBuffer() {
        return this.buffer;
    }

    public void setBuffer(ItemStack stack) {
        this.buffer = stack;
        this.setChanged();
    }

    /** 掉落物落入：空袋或同物未满才收（原地扣减源堆）。 */
    public void acceptDrop(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (this.buffer.isEmpty()) {
            this.buffer = stack.split(Math.min(stack.getCount(), stack.getMaxStackSize()));
            this.setChanged();
        } else if (ItemStack.isSameItemSameComponents(stack, this.buffer)) {
            int room = this.buffer.getMaxStackSize() - this.buffer.getCount();
            int taken = Math.min(stack.getCount(), room);
            if (taken > 0) {
                stack.split(taken);
                this.buffer.grow(taken);
                this.setChanged();
            }
        }
    }

    /** 取走全部袋内货。 */
    public ItemStack takeBuffer() {
        ItemStack out = this.buffer;
        this.buffer = ItemStack.EMPTY;
        this.setChanged();
        return out;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.buffer.isEmpty()) {
            tag.put("Buffer", this.buffer.saveOptional(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Buffer")) {
            this.buffer = ItemStack.parseOptional(registries, tag.getCompound("Buffer"));
        } else {
            this.buffer = ItemStack.EMPTY;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (!this.buffer.isEmpty()) {
            tag.put("Buffer", this.buffer.saveOptional(registries));
        }
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
