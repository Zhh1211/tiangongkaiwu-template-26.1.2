package com.example.tiangongkaiwu.block.entity;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.block.JianBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.Nullable;

/**
 * 梘的方块实体：只存「装的是哪种流体」（FluidStack）。
 *
 * <p>水位（LEVEL×SUB）放在方块状态里，供传播规则与贴图直接读；
 * 流体种类放 BE，随区块存档并同步到客户端（BER 按它取贴图染色，D12）。
 * 约定：1 级有效水位 = {@link #MB_PER_LEVEL} mB，BE 里的液量与方块状态水位
 * 由 {@link JianBlock} 的 tick 负责对账。
 *
 * <p>同时对外暴露标准 {@link IFluidHandler}（D9）：批 3 的「筒」往里注水、
 * 以后三卷的卤水/油/熔液都走这个口子。
 */
public class JianBlockEntity extends BlockEntity {

    /** 1 级有效水位对应的液量（mB）。16 级封顶 → 1600 mB。 */
    public static final int MB_PER_LEVEL = 100;
    /** 满槽容量（mB）。 */
    public static final int CAPACITY = 16 * MB_PER_LEVEL;

    private FluidStack fluid = FluidStack.EMPTY;
    private final FluidHandler handler = new FluidHandler();

    public JianBlockEntity(BlockPos pos, BlockState state) {
        super(TiangongKaiwu.JIAN_BE_TYPE.get(), pos, state);
    }

    // ============ 流体存取 ============

    public FluidStack getFluid() {
        return this.fluid;
    }

    /** 设置流体种类（不改液量；空 stack 视为清空）。 */
    public void setFluid(FluidStack stack) {
        this.fluid = stack == null ? FluidStack.EMPTY : stack;
        this.setChanged();
        this.sync();
    }

    /** 把 BE 液量对账成方块状态的有效水位（由 JianBlock.tick 调用）。 */
    public void reconcile(int effLevel) {
        int target = Math.clamp(effLevel, 0, 16) * MB_PER_LEVEL;
        if (effLevel <= 0) {
            if (!this.fluid.isEmpty()) {
                this.fluid = FluidStack.EMPTY;
                this.setChanged();
                this.sync();
            }
            return;
        }
        if (this.fluid.isEmpty()) {
            this.fluid = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, target);
            this.setChanged();
            this.sync();
            return;
        }
        if (this.fluid.getAmount() != target) {
            this.fluid = new FluidStack(this.fluid.getFluid(), target);
            this.setChanged();
            this.sync();
        }
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

    // ============ 标准 IFluidHandler（D9） ============

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
            return CAPACITY;
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
            if (JianBlockEntity.this.fluid.isEmpty()) {
                int accepted = Math.min(resource.getAmount(), CAPACITY);
                if (action.execute()) {
                    JianBlockEntity.this.fluid = new FluidStack(resource.getFluid(), accepted);
                    JianBlockEntity.this.setChanged();
                    JianBlockEntity.this.sync();
                }
                return accepted;
            }
            if (!FluidStack.isSameFluidSameComponents(resource, JianBlockEntity.this.fluid)) {
                return 0;
            }
            int accepted = Math.min(resource.getAmount(), CAPACITY - JianBlockEntity.this.fluid.getAmount());
            if (action.execute() && accepted > 0) {
                JianBlockEntity.this.fluid.grow(accepted);
                JianBlockEntity.this.setChanged();
                JianBlockEntity.this.sync();
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
                JianBlockEntity.this.fluid.shrink(drained);
                if (JianBlockEntity.this.fluid.isEmpty()) {
                    JianBlockEntity.this.fluid = FluidStack.EMPTY;
                }
                JianBlockEntity.this.setChanged();
                JianBlockEntity.this.sync();
            }
            return out;
        }
    }
}
