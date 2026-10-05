package com.example.tiangongkaiwu.block.entity;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.block.JianBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.Nullable;

/**
 * 梘段的方块实体（D16 连通器模型）：存本段持有的流体（FluidStack，mB 计量）。
 *
 * <p><b>段 = 网络的显示窗口，不是独立水箱</b>：水在「梘网」里守恒流动（重力分配：
 * 高段向低段转移、正下方直落），每段的液量是分配的结果。真实流体量以 mB 计，
 * **1 源方块 = 1B = 1000mB = 1 段满**（D16，社区共识单位；旧「级」抽象已退役）。
 *
 * <p>对外暴露标准 {@link IFluidHandler}（D9）：筒、卤水、油、熔液都走这个口子。
 */
public class JianBlockEntity extends BlockEntity {

    /** 每段容量（mB）= 1B = 1 个源方块。 */
    public static final int SEGMENT_CAPACITY = 1000;

    private FluidStack fluid = FluidStack.EMPTY;
    private final FluidHandler handler = new FluidHandler();

    public JianBlockEntity(BlockPos pos, BlockState state) {
        super(TiangongKaiwu.JIAN_BE_TYPE.get(), pos, state);
    }

    // ============ 流体存取 ============

    public FluidStack getFluid() {
        return this.fluid;
    }

    /** 本段当前液量（mB）。 */
    public int getMb() {
        return this.fluid.isEmpty() ? 0 : this.fluid.getAmount();
    }

    /**
     * 接受流体：同种或空段才收，容量 {@link #SEGMENT_CAPACITY} 封顶。
     *
     * @return 实际接受的 mB
     */
    public int accept(Fluid fluid, int amount) {
        if (fluid == null || amount <= 0) {
            return 0;
        }
        if (this.fluid.isEmpty()) {
            int accepted = Math.min(amount, SEGMENT_CAPACITY);
            this.fluid = new FluidStack(fluid, accepted);
            this.setChanged();
            this.sync();
            return accepted;
        }
        if (!FluidStack.isSameFluidSameComponents(new FluidStack(fluid, 1), this.fluid)) {
            return 0;
        }
        int accepted = Math.min(amount, SEGMENT_CAPACITY - this.fluid.getAmount());
        if (accepted > 0) {
            this.fluid.grow(accepted);
            this.setChanged();
            this.sync();
        }
        return accepted;
    }

    /**
     * 排出流体：不足则排空为止。
     *
     * @return 实际排出的 mB
     */
    public int drainMb(int amount) {
        if (amount <= 0 || this.fluid.isEmpty()) {
            return 0;
        }
        int drained = Math.min(amount, this.fluid.getAmount());
        this.fluid.shrink(drained);
        if (this.fluid.isEmpty()) {
            this.fluid = FluidStack.EMPTY;
        }
        this.setChanged();
        this.sync();
        return drained;
    }

    public IFluidHandler getFluidHandler() {
        return this.handler;
    }

    // ============ 持久化与客户端同步 ============

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.fluid.isEmpty()) {
            tag.put("Fluid", this.fluid.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Fluid")) {
            this.fluid = FluidStack.parse(registries, tag.get("Fluid")).orElse(FluidStack.EMPTY);
        } else {
            this.fluid = FluidStack.EMPTY;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (!this.fluid.isEmpty()) {
            tag.put("Fluid", this.fluid.save(registries));
        }
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sync() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        }
    }

    // ============ 标准 IFluidHandler（D9，容量=段容量） ============

    private class FluidHandler implements IFluidHandler {

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return JianBlockEntity.this.fluid;
        }

        @Override
        public int getTankCapacity(int tank) {
            return SEGMENT_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return stack != null && !stack.isEmpty();
        }

        @Override
        public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
            if (resource == null || resource.isEmpty()) {
                return 0;
            }
            int accepted = JianBlockEntity.this.accept(resource.getFluid(), resource.getAmount());
            if (action.execute() && accepted > 0) {
                // accept 内部已写入并同步
            }
            return accepted;
        }

        @Override
        public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) {
            if (resource == null || resource.isEmpty() || JianBlockEntity.this.fluid.isEmpty()) {
                return FluidStack.EMPTY;
            }
            if (!FluidStack.isSameFluidSameComponents(resource, JianBlockEntity.this.fluid)) {
                return FluidStack.EMPTY;
            }
            return this.drain(resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
            if (maxDrain <= 0 || JianBlockEntity.this.fluid.isEmpty()) {
                return FluidStack.EMPTY;
            }
            int drained = Math.min(maxDrain, JianBlockEntity.this.fluid.getAmount());
            FluidStack out = new FluidStack(JianBlockEntity.this.fluid.getFluid(), drained);
            if (action.execute()) {
                JianBlockEntity.this.drainMb(drained);
            }
            return out;
        }
    }
}
