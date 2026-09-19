package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
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
import net.minecraft.world.level.block.Blocks;
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
 * - waterlogged：田面还有水时保持 true；田水见底（{@link #MOISTURE} &lt; {@link #WATER_VISIBLE_MIN}）自动置 false，
 *   水面可视地退去，同时停止生长 —— 提醒玩家"该挑水了"。
 * - MOISTURE 0–7：田水存量。**水库会自己少**：降雨/邻水回充，否则按底材蒸发（书曰「厥土沙、泥、磽、膩，
 *   隨方不一，有三日即乾者，有半月後乾者」）；归零 → 转 {@link RiceStalkBlockDry}（保 AGE，倒水可救回）。
 * - AGE 0–7 沿用 {@link BlockStateProperties#AGE_7}；满 7 且上方空气 → 自动在正上方放置 {@link RicePanicleBlock}。
 * - 打秆（age==7）掉 1 粒稻谷保本；age<7 无掉落。
 * - 打上部稻穗（{@link RicePanicleBlock}）→ 由 panicle 把下方秆 AGE 重置为 3，秆可视地"缩回半截"再长穗。
 * - 玩家手持骨粉可以催熟（v1 暂不弱化，留给 #34 引入粪肥后处理）。
 *
 * 设计依据见 docs/乃粒稻作玩法设计.md（水利 A 批）与 docs/天工开物·乃粒原文.md「水利」节。
 */
public class RiceStalkBlock extends Block implements SimpleWaterloggedBlock, BonemealableBlock {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** 田水存量 0–7（满 = 刚灌过）。书里"三日即干 / 半月后干"的载体。 */
    public static final IntegerProperty MOISTURE = IntegerProperty.create("moisture", 0, 7);

    public static final int MAX_AGE = 7;
    /** 收穗后秆复位阶段——可视地"缩回半截再长穗"。 */
    public static final int POST_HARVEST_AGE = 3;
    public static final int MAX_MOISTURE = 7;
    /** 田面见底阈值：低于此值水面消失（视觉提示 + 停止生长）。 */
    public static final int WATER_VISIBLE_MIN = 3;

    // ====== 蒸发速率：底材定"保水天数"（随机 tick 概率；默认 randomTickSpeed=3 时折算） ======
    /** 沙 —— 约 3 天（书：三日即乾者）。 */
    private static final float DRY_SAND = 0.13f;
    /** 磽＝瘦土（砂砾/粗泥）—— 约 4 天。 */
    private static final float DRY_LEAN = 0.10f;
    /** 泥（泥土/草方块/泥巴）—— 约 6–7 天。 */
    private static final float DRY_MUD = 0.066f;
    /** 膩＝肥土（黏土）—— 约半月（书：有半月後乾者）。 */
    private static final float DRY_FAT = 0.026f;
    /** 兜底 —— 约 8 天。 */
    private static final float DRY_OTHER = 0.05f;

    /** 雨天回充概率（书：天澤不降，則人力挽水以濟 —— 下雨不用挑水，放晴才要）。 */
    private static final float RAIN_REFILL = 0.25f;
    /** 邻水/连片水田回充概率（沟渠引水；比蒸发快，故修了水渠就能维持住田水）。 */
    private static final float CHANNEL_REFILL = 1.0f;

    public RiceStalkBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AGE, 0)
                .setValue(WATERLOGGED, true)
                .setValue(MOISTURE, MAX_MOISTURE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, WATERLOGGED, MOISTURE);
    }

    // ====== 水属性：waterlogged 时对外表现"含水"（水面渲染/湿润判定都用得上） ======
    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean canPlaceLiquid(Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return !state.getValue(WATERLOGGED) && fluid == Fluids.WATER;
    }

    /** 水桶倒下：除了置位 waterlogged，还把 MOISTURE 灌满（否则刚浇完立刻又干）。 */
    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        if (state.getValue(WATERLOGGED) || fluid.getType() != Fluids.WATER) {
            return false;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(WATERLOGGED, true).setValue(MOISTURE, MAX_MOISTURE), 3);
            level.scheduleTick(pos, fluid.getType(), fluid.getType().getTickDelay(level));
        }
        return true;
    }

    // ====== 存活条件：底部须为可作田的土（泥土系/泥；书里的"沙、泥、磽、膩"） ======
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return isPaddySoil(level.getBlockState(pos.below()));
    }

    /** 可作稻田的底土：泥土系、泥巴，以及书里点名的沙（沙田）、砂砾/粗泥（磽）、黏土（膩）。 */
    public static boolean isPaddySoil(BlockState below) {
        Block b = below.getBlock();
        return below.is(BlockTags.DIRT)
                || b == Blocks.MUD
                || b == Blocks.SAND
                || b == Blocks.RED_SAND
                || b == Blocks.GRAVEL
                || b == Blocks.CLAY;
    }

    // ====== 随机 tick：先结算田水，再生长 ======
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (tickWater(state, level, pos, random)) {
            return; // 本 tick 田水发生了变化，生长让位一次
        }
        if (!state.getValue(WATERLOGGED)) {
            return; // 田面见底 → 停止生长
        }

        int age = state.getValue(AGE);
        if (age < MAX_AGE) {
            // 平均约 8 个 random tick 长一档（玩家感知 ≈ 几分钟）
            if (random.nextInt(8) == 0) {
                level.setBlock(pos, state.setValue(AGE, age + 1), 2);
            }
            return;
        }

        // age == 7：尝试在正上方抽穗（上方需为空气；多次 tick 重试）
        trySpawnPanicle(level, pos);
    }

    /** 秆成熟后尝试在正上方长出稻穗（仅当上方为空）。 */
    private static void trySpawnPanicle(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos.above()).isAir()) {
            level.setBlock(pos.above(), TiangongKaiwu.RICE_PANICLE.get().defaultBlockState(), 2);
        }
    }

    // ====== 田水结算（水利 A） ======

    /**
     * 田水每随机 tick 结算一次：
     * ① 正在下雨 → 不蒸发，慢慢回满（天泽）；
     * ② 旁边有水（沟渠）或连片稻田还有水 → 不蒸发，慢慢回满；
     * ③ 否则按底材概率蒸发；见底则转枯秆。
     *
     * @return true 表示本 tick 已改写方块状态
     */
    private boolean tickWater(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // ① 天泽：下雨
        if (level.isRainingAt(pos.above())) {
            return random.nextFloat() < RAIN_REFILL && refill(state, level, pos);
        }
        // ② 沟渠 / 连片水田
        if (hasWaterNeighbor(level, pos)) {
            return random.nextFloat() < CHANNEL_REFILL && refill(state, level, pos);
        }
        // ③ 蒸发
        if (random.nextFloat() < drynessChance(level.getBlockState(pos.below()))) {
            int moisture = state.getValue(MOISTURE) - 1;
            if (moisture <= 0) {
                // 干涸见底 → 枯秆（把 AGE 一并带走，倒水可原样救回）
                level.setBlock(pos, TiangongKaiwu.RICE_STALK_DRY.get().defaultBlockState()
                        .setValue(RiceStalkBlockDry.AGE, state.getValue(AGE)), 3);
            } else {
                level.setBlock(pos, withMoisture(state, moisture), 2);
            }
            return true;
        }
        return false;
    }

    /** 回充一格田水；已满则不写。 */
    private static boolean refill(BlockState state, ServerLevel level, BlockPos pos) {
        int moisture = state.getValue(MOISTURE);
        if (moisture >= MAX_MOISTURE) {
            return false;
        }
        level.setBlock(pos, withMoisture(state, moisture + 1), 2);
        return true;
    }

    /** 邻格有水，或邻格是"还带水的"同类稻田 → 视为沟渠/连片供水。 */
    private static boolean hasWaterNeighbor(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(dir);
            if (level.getFluidState(neighbor).is(FluidTags.WATER)) {
                return true;
            }
            BlockState neighborState = level.getBlockState(neighbor);
            if (neighborState.is(TiangongKaiwu.RICE_STALK.get()) && neighborState.getValue(WATERLOGGED)) {
                return true;
            }
        }
        return false;
    }

    /** 底材 → 蒸发概率（书：沙、泥、磽、膩，隨方不一）。 */
    private static float drynessChance(BlockState below) {
        Block b = below.getBlock();
        if (b == Blocks.SAND || b == Blocks.RED_SAND) {
            return DRY_SAND;
        }
        if (b == Blocks.GRAVEL || b == Blocks.COARSE_DIRT) {
            return DRY_LEAN;
        }
        if (b == Blocks.CLAY) {
            return DRY_FAT;
        }
        if (below.is(BlockTags.DIRT) || b == Blocks.MUD) {
            return DRY_MUD;
        }
        return DRY_OTHER;
    }

    /** 写田水时同步 waterlogged：见底了水面就该退去。 */
    public static BlockState withMoisture(BlockState state, int moisture) {
        int clamped = Math.max(0, Math.min(MAX_MOISTURE, moisture));
        return state.setValue(MOISTURE, clamped).setValue(WATERLOGGED, clamped >= WATER_VISIBLE_MIN);
    }

    // ====== 骨粉催熟（v1 暂接受；#34 引入粪肥后弱化/无效化） ======
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
        if (age >= MAX_AGE) {
            return;
        }
        int newAge = Math.min(MAX_AGE, age + 1 + random.nextInt(2));
        level.setBlock(pos, state.setValue(AGE, newAge), 2);
        // 骨粉催到顶（AGE 7）时立刻尝试抽穗，否则玩家要干等下一次随机 tick
        if (newAge == MAX_AGE) {
            trySpawnPanicle(level, pos);
        }
    }

    // ====== 主动舀水：空桶右键 → 取走一格水（不是立刻枯死） ======
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(Items.BUCKET) && state.getValue(WATERLOGGED)) {
            if (level.isClientSide) {
                return ItemInteractionResult.sidedSuccess(true);
            }
            // 一桶取走 3 格田水；取到见底则转枯秆（保 AGE）
            int left = state.getValue(MOISTURE) - 3;
            if (left <= 0) {
                level.setBlock(pos, TiangongKaiwu.RICE_STALK_DRY.get().defaultBlockState()
                        .setValue(RiceStalkBlockDry.AGE, state.getValue(AGE)), 3);
            } else {
                level.setBlock(pos, withMoisture(state, left), 3);
            }
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
