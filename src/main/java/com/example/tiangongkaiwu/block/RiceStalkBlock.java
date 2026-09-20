package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
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
 * - 玩家手持骨粉可以催熟，但**已弱化**（书里没提骨粉）。稻宜那套「对症肥料」才是正途：
 *   粪肥通用（人畜穢遺）；骨灰／石灰只宜**冷浆土**（黏土田）；烧土（潜行 + 薪柴）只宜**坚紧土**（砂砾／粗泥田）。
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
    // ⚠️ 节奏说明（2026-09-20 实测反馈后调整）：书里的「三日即乾 / 半月後乾」若按真实 MC 日
    // 折算，泥土田要一个多小时才掉完水、十几分钟才掉一格，玩家根本观察不到（"种上去不会干"）。
    // 故整体压缩约 5 倍，**保留相对关系**（沙最快、黏土最慢，约 5 倍差），绝对时长压到可玩尺度。
    /** 沙 —— 最快（满水约 12 分钟见底）。 */
    private static final float DRY_SAND = 0.65f;
    /** 磽＝瘦土（砂砾/粗泥）—— 约 16 分钟。 */
    private static final float DRY_LEAN = 0.50f;
    /** 泥（泥土/草方块/泥巴）—— 约 24 分钟。 */
    private static final float DRY_MUD = 0.33f;
    /** 膩＝肥土（黏土）—— 最慢，约 1 小时（书：有半月後乾者）。 */
    private static final float DRY_FAT = 0.13f;
    /** 兜底 —— 约 32 分钟。 */
    private static final float DRY_OTHER = 0.25f;

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

    /** 土性之一：**冷浆土**（书「土性帶冷漿者，宜骨灰蘸秧根……石灰淹苗足」）。黏土保水最强、地温最低。 */
    public static boolean isColdSlurrySoil(BlockState below) {
        return below.getBlock() == Blocks.CLAY;
    }

    /** 土性之一：**坚紧土**（书「土脈堅緊者，宜耕壟，疊塊壓薪而燒之」）。砂砾/粗泥质地板结瘠薄。 */
    public static boolean isTightSoil(BlockState below) {
        Block b = below.getBlock();
        return b == Blocks.GRAVEL || b == Blocks.COARSE_DIRT;
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

    // ====== 催熟（骨粉已弱化；主力是稻宜那套「对症肥料」） ======

    /** 骨粉利用率：**已弱化**——书里没提骨粉，只有「凡禽獸骨」烧成的骨灰才算对症的药。 */
    private static final float BONEMEAL_CHANCE = 0.35f;

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(AGE) < MAX_AGE && state.getValue(WATERLOGGED);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return random.nextFloat() < BONEMEAL_CHANCE;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        boost(level, pos, state, 1);
    }

    /** 推进 AGE 若干档；到顶则立刻抽穗（否则玩家要干等下一次随机 tick）。 */
    private static boolean boost(ServerLevel level, BlockPos pos, BlockState state, int steps) {
        int age = state.getValue(AGE);
        if (age >= MAX_AGE) {
            trySpawnPanicle(level, pos);
            return false;
        }
        int newAge = Math.min(MAX_AGE, age + steps);
        level.setBlock(pos, state.setValue(AGE, newAge), 2);
        if (newAge == MAX_AGE) {
            trySpawnPanicle(level, pos);
        }
        return true;
    }

    // ====== 主动舀水：空桶右键 → 取走一格水（不是立刻枯死） ======
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        // ====== 稻宜·粪田：对症施肥（书：土性不同改土法不同，用错「不宜也」） ======
        if (!stack.isEmpty() && !level.isClientSide) {
            BlockState soil = level.getBlockState(pos.below());
            boolean logs = stack.is(ItemTags.LOGS) && player != null && player.isShiftKeyDown();
            String used = null;
            if (stack.is(TiangongKaiwu.FEN_FEI.get())) {
                used = "block.tiangongkaiwu.fen_fei.used";            // 人畜穢遺，普天之所同也
            } else if (logs) {
                if (!isTightSoil(soil)) {
                    return refuse(level, pos, player, "block.tiangongkaiwu.shao_tu.wrong");
                }
                used = "block.tiangongkaiwu.shao_tu.used";            // 坚紧土：耕壟壓薪而燒之
            } else if (stack.is(TiangongKaiwu.GU_HUI.get()) || stack.is(TiangongKaiwu.SHI_HUI.get())) {
                if (!isColdSlurrySoil(soil)) {
                    return refuse(level, pos, player, "block.tiangongkaiwu.hui.wrong");
                }
                used = stack.is(TiangongKaiwu.GU_HUI.get())
                        ? "block.tiangongkaiwu.gu_hui.used"           // 冷浆土：骨灰蘸秧根
                        : "block.tiangongkaiwu.shi_hui.used";         // 冷浆土：石灰淹苗足
            }
            if (used != null) {
                return fertilize(stack, state, level, pos, player, used);
            }
        }

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
            // 舀完给个明确反馈（此前玩家完全不知道舀掉了多少，2026-09-20 反馈）
            if (player != null) {
                player.displayClientMessage(Component.translatable(
                        "block.tiangongkaiwu.rice.field_water",
                        Math.max(0, left), MAX_MOISTURE), true);
            }
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

    // ====== 稻宜·粪田：对症施肥（书「勤農糞田，多方以助之」） ======

    /** 施一次肥：推进 2~3 档（必成，比骨粉强）。田里没水、或已经熟透时不消耗，只提醒。 */
    private static ItemInteractionResult fertilize(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, String messageKey) {
        if (!state.getValue(WATERLOGGED)) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("block.tiangongkaiwu.fertilize.dry"), true);
            }
            return ItemInteractionResult.sidedSuccess(false);
        }
        if (state.getValue(AGE) >= MAX_AGE) {
            trySpawnPanicle((ServerLevel) level, pos);
            if (player != null) {
                player.displayClientMessage(Component.translatable("block.tiangongkaiwu.fertilize.ripe"), true);
            }
            return ItemInteractionResult.sidedSuccess(false);
        }
        ServerLevel server = (ServerLevel) level;
        boost(server, pos, state, 2 + server.getRandom().nextInt(2));
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable(messageKey), true);
        }
        server.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 6, 0.3D, 0.3D, 0.3D, 0.0D);
        level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 0.6F, 1.2F);
        return ItemInteractionResult.sidedSuccess(false);
    }

    /** 用错土性：书里的「不宜也」——不消耗，只提醒（并挡下"手里拿着柴想放方块"的误伤）。 */
    private static ItemInteractionResult refuse(Level level, BlockPos pos, Player player, String messageKey) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(messageKey), true);
        }
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4F, 0.7F);
        return ItemInteractionResult.sidedSuccess(false);
    }
}

