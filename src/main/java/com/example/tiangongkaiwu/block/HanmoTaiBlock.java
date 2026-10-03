package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.block.entity.HanmoTaiBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

public class HanmoTaiBlock extends Block implements EntityBlock {

    // 水平朝向：放置时正面（砚台/笔一侧）转向玩家
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public HanmoTaiBlock(Properties properties) {
        super(properties);
        // 默认朝南（模型未旋转时的正面方向），旧存档读出来也是这个值
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * 放置时面向玩家：玩家朝向的反方向就是台面正面。
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    // 支持结构方块旋转/镜像，保持朝向属性同步
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ============================================================
    // 方块实体：翰墨台是容器方块，材料存放在方块内
    // ============================================================
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HanmoTaiBlockEntity(pos, state);
    }

    /**
     * 方块被破坏/替换时，把方块内尚未取走的材料掉落出来。
     * 方块本体仍按战利品表正常掉落。
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof HanmoTaiBlockEntity hanmo) {
                Containers.dropContents(level, pos, hanmo);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // 重要：NeoForge 1.21.1 中 Block.use(...) 已不存在，
    // 右键交互被拆成 useWithoutItem（空手）与 useItemOn（手持物品）两个方法。
    // 本轮只实现空手右键打开界面；手持物品右键暂走原版默认（正常放置/使用物品）。
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof HanmoTaiBlockEntity hanmo) {
                player.openMenu(hanmo);
            }
        }
        return InteractionResult.CONSUME;
    }
}
