package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 水培稻的下部秆块（两段式结构中的下段，参考农夫乐事 RiceBlock）。
 *
 * - waterlogged：必须保持 true 才能生长；空桶右键取水即转为 {@link RiceStalkBlockDry}（演示版稻灾）。
 * - AGE 0–7 沿用 {@link BlockStateProperties#AGE_7}；满 7 且上方空气 → 自动在正上方放置 {@link RicePanicleBlock}。
 * - 打秆（age==7）掉 1 粒稻谷保本；age<7 无掉落。
 * - 打上部稻穗（{@link RicePanicleBlock}）→ 由 panicle 把下方秆 AGE 重置为 3，秆可视地"缩回半截"再长穗。
 * - 玩家手持骨粉可以催熟（v1 暂不弱化，留给 #32 后续批）。
 */
public class RiceStalkBlock extends Block implements SimpleWaterloggedBlock, BonemealableBlock {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final int MAX_AGE = 7;
    /** 收穗后秆复位阶段——可视地"缩回半截再长穗"。 */
    public static final int POST_HARVEST_AGE = 3;

    public RiceStalkBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AGE, 0)
                .setValue(WATERLOGGED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, WATERLOGGED);
    }

    // ====== 水属性：水 logged 时对外表现"含水"（农田湿润/浸水判定都用得上） ======
    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean canPlaceLiquid(Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return !state.getValue(WATERLOGGED) && fluid == Fluids.WATER;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        // 一般由 SimpleWaterloggedBlock 默认实现处理（仅切 waterlogged）。本块保持默认即可。
        return SimpleWaterloggedBlock.super.placeLiquid(level, pos, state, fluid);
    }

    // ====== 存活条件：底部必须泥土系 / 泥（黏土/沙下不放） ======
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(BlockTags.DIRT) || below.getBlock() == net.minecraft.world.level.block.Blocks.MUD;
    }

    // ====== 生长：仅在含水时随机 tick 递增 ======
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WATERLOGGED)) return;

        int age = state.getValue(AGE);
        if (age < MAX_AGE) {
            // 平均约 8 个 random tick 长一档（玩家感知 ≈ 几分钟）
            if (random.nextInt(8) == 0) {
                level.setBlock(pos, state.setValue(AGE, age + 1), 2);
            }
            return;
        }

        // age == 7：尝试在正上方抽穗（上方需为空气；多次 tick 重试）
        BlockPos above = pos.above();
        if (level.getBlockState(above).isAir()) {
            level.setBlock(above, TiangongKaiwu.RICE_PANICLE.get().defaultBlockState(), 2);
        }
    }

    // ====== 骨粉催熟（v1 暂接受；后续 #32 与 #34 引入粪肥后弱化/无效化） ======
    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(AGE) < MAX_AGE && state.getValue(WATERLOGGED);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return random.nextFloat() < 0.8F;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        int age = state.getValue(AGE);
        if (age >= MAX_AGE) return;
        int newAge = Math.min(MAX_AGE, age + 1 + random.nextInt(2));
        level.setBlock(pos, state.setValue(AGE, newAge), 2);
    }

    // ====== 主动舀水：空桶右键 → 转枯秆 + 给玩家水桶（演示版稻灾入口） ======
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(Items.BUCKET) && state.getValue(WATERLOGGED)) {
            if (level.isClientSide) {
                return ItemInteractionResult.sidedSuccess(true);
            }
            // 转枯秆
            level.setBlock(pos, TiangongKaiwu.RICE_STALK_DRY.get().defaultBlockState(), 3);
            // 给玩家水桶
            if (player != null && !player.getAbilities().instabuild) {
                ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);
                if (stack.getCount() == 1) {
                    player.setItemInHand(hand, waterBucket);
                } else {
                    stack.shrink(1);
                    if (!player.getInventory().add(waterBucket)) {
                        player.drop(waterBucket, false);
                    }
                }
            }
            level.playSound(player, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
            return ItemInteractionResult.sidedSuccess(false);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    // ====== 打秆：age==7 掉 1 粒保本；age<7 无掉落；上方 panicle 由 neighborChanged 自毁带掉落 ======
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              net.minecraft.world.level.block.entity.BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (state.getValue(AGE) == MAX_AGE) {
            popResource(level, pos, new ItemStack(TiangongKaiwu.RICE_GRAIN.get(), 1));
        }
    }
}