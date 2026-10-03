package com.example.tiangongkaiwu.block.entity;

import com.example.tiangongkaiwu.TiangongKaiwu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 机构方块实体（D15 装置多方块化的通用件）。
 *
 * <p>记录两件事：
 * <ul>
 * <li>{@link #original}：成型时被替换掉的原方块（拆解时原样还原给玩家）；</li>
 * <li>运动语义与动画参数由方块状态（{@code semantic}）和所在装置的核心格约定——
 *     碓的杵位恒在核心正上方，渲染器直接向下找核心。</li>
 * </ul>
 */
public class MechanismBlockEntity extends BlockEntity {

    /** 成型时被替换的原方块（air = 该位置本来就空）。 */
    public BlockState original = Blocks.AIR.defaultBlockState();

    public MechanismBlockEntity(BlockPos pos, BlockState state) {
        super(TiangongKaiwu.MECHANISM_BE_TYPE.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Original", NbtUtils.writeBlockState(this.original));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.original = NbtUtils.readBlockState(
                registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("Original"));
    }
}
