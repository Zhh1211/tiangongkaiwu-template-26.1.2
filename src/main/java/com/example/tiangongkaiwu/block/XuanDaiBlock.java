package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.block.entity.XuanDaiBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;

import com.example.tiangongkaiwu.block.entity.JianBlockEntity;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;

/**
 * 悬袋（物流一期 D19 §11，粹精卷：「悬袋磨上，上宽下窄，贮麦数斗于中，溜入磨眼」）。
 *
 * <p>古代喂料器本尊——物流的「重力接口」：
 * <ul>
 * <li><b>吸模式（默认）</b>：掉落物落入即收进袋；袋内货优先漏给<b>下方容器</b>（漏斗职能，
 *     缓慢限速），其次喂给<b>邻接龙骨段</b>（acceptCargo 自动认）；都没有则袋内暂存；</li>
 * <li><b>吐模式</b>（墨斗右键切换）：从上方容器抽一组货 → 以掉落物弹出（发货卸货）；</li>
 * <li>贴龙骨段**自动判方向**：链尾弹出物落袋=接货，袋有货=喂链——位置即配置，墨斗不干预方向。</li>
 * </ul>
 */
public class XuanDaiBlock extends Block implements EntityBlock {

    /** true=吸（收货） false=吐（卸货）。 */
    public static final BooleanProperty SUCKING = BooleanProperty.create("sucking");

    /** 结算周期（tick）。 */
    public static final int INTERVAL = 20;

    private static final VoxelShape SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 12.0D, 13.0D);

    public XuanDaiBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(SUCKING, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SUCKING);
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                               BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** 墨斗右键：切吸⇄吐。 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand,
                                              net.minecraft.world.phys.BlockHitResult hit) {
        if (!stack.isEmpty() && stack.getItem() instanceof com.example.tiangongkaiwu.item.MoDouItem) {
            if (level.isClientSide) {
                return ItemInteractionResult.sidedSuccess(true);
            }
            boolean nowSucking = !state.getValue(SUCKING);
            level.setBlock(pos, state.setValue(SUCKING, nowSucking), 3);
            if (player != null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(nowSucking
                        ? "block.tiangongkaiwu.xuan_dai.mode_suck"
                        : "block.tiangongkaiwu.xuan_dai.mode_spit"), true);
            }
            level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.5F, 1.2F);
            return ItemInteractionResult.sidedSuccess(false);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /** 掉落物落入 → 吸模式收进袋。 */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !state.getValue(SUCKING) || !(entity instanceof ItemEntity drop)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof XuanDaiBlockEntity be)) {
            return;
        }
        be.acceptDrop(drop.getItem());
        if (drop.getItem().isEmpty()) {
            entity.discard();
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, INTERVAL);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof XuanDaiBlockEntity be)) {
            return;
        }
        if (state.getValue(SUCKING)) {
            tickSuck(level, pos, be);
        } else {
            tickSpit(level, pos, be);
        }
        level.scheduleTick(pos, this, INTERVAL);
    }

    /** 吸模式：袋内货 → 下方容器 → 邻接龙骨段 → 暂存。 */
    private void tickSuck(ServerLevel level, BlockPos pos, XuanDaiBlockEntity be) {
        ItemStack cargo = be.getBuffer();
        if (cargo.isEmpty()) {
            return;
        }
        // ① 下方容器（漏斗职能：标准 IItemHandler）
        ItemStack probe = cargo.copy();
        IItemHandler below = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.below(), Direction.UP);
        if (below != null) {
            for (int slot = 0; slot < below.getSlots() && !probe.isEmpty(); slot++) {
                probe = below.insertItem(slot, probe, false);
            }
            if (probe.isEmpty()) {
                be.takeBuffer();
                return;
            }
        }
        // ② 邻接龙骨段（喂链）
        for (Direction dir : Direction.values()) {
            if (level.getBlockEntity(pos.relative(dir)) instanceof JianBlockEntity jbe
                    && jbe.acceptCargo(cargo.copy()) > 0) {
                // 段收多少算多少（acceptCargo 内部 split 扣源堆）
                be.setBuffer(cargo.copy());
                return;
            }
        }
        // ③ 暂存（袋满即止，不消失）
    }

    /** 吐模式：上方容器抽一组 → 掉落物弹出（发货卸货）。 */
    private void tickSpit(ServerLevel level, BlockPos pos, XuanDaiBlockEntity be) {
        if (!be.getBuffer().isEmpty()) {
            return; // 袋里还有货：先出完
        }
        IItemHandler above = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.above(), Direction.DOWN);
        if (above == null) {
            return;
        }
        for (int slot = 0; slot < above.getSlots(); slot++) {
            ItemStack in = above.getStackInSlot(slot);
            if (!in.isEmpty()) {
                ItemStack out = above.extractItem(slot, Math.min(in.getCount(), in.getMaxStackSize()), false);
                if (!out.isEmpty()) {
                    Block.popResource(level, pos.below(), out);
                    return;
                }
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new XuanDaiBlockEntity(pos, state);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (blockEntity instanceof XuanDaiBlockEntity be && !be.getBuffer().isEmpty()) {
            Block.popResource(level, pos, be.takeBuffer());
        }
    }
}
