package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 筒车（水车）：书里「凡河濱有制筒車者，堰陂障流，繞於車下，激輪使轉，挽水入筒，
 * 一一傾於梘內，流入畝中。晝夜不息，百畝無憂（不用水時，栓木礙止）」。
 *
 * <p><b>三乘三的大轮子</b>：一格放下会**自动展开成 3×3**（放一格、长成一台），
 * 挖任意一格则整台收回、只掉一个物品。轮子立在竖直平面里，`plane` 记轮面朝向（x/z）。
 *
 * <p><b>要激流才转</b>：只有车下那排（底排三格及其下方）有**流动的水**（非水源）时才转——
 * 所以玩家得自己在河滨垒堰，"堰陂障流"，把水逼到车下。静水塘里它不转。
 *
 * <p><b>两件事</b>：① 提水灌田——挨着的梘会从它这里"接到水"，由梘把水引到田里；
 * ② 输出动力 12 点给传动轴（书里效率刻度最高的装置，"昼夜不息，百亩无忧"）。
 *
 * <p><b>栓木碍止</b>：空手右键可插/拔木栓——插上就停（动力与提水都停），拔掉复转。
 */
public class TongCheBlock extends Block {

    /** 部件索引 0–8（3×3 自左上起按行排；4 = 中心/核心）。 */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 8);
    /** 轮面朝向（轴的方向）：x 或 z。 */
    public static final EnumProperty<Direction.Axis> PLANE =
            EnumProperty.create("plane", Direction.Axis.class, Direction.Axis.X, Direction.Axis.Z);
    /** 是否插了木栓（true = 停）。 */
    public static final BooleanProperty PLUGGED = BooleanProperty.create("plugged");
    /** 是否在转（水够 + 没插栓）。核心算出后同步给 9 格，供梘与传动轴读取。 */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public static final int CORE_PART = 4;
    /** 动力输出（书：昼夜不息，百畝無憂 —— 四种动力源里最强）。 */
    public static final int POWER_OUTPUT = 12;
    /** 结算间隔：每 20 tick（1 秒）一次。 */
    public static final int INTERVAL = 20;
    /** 是否严格要求"流动的水"（书要"激轮"）。调 false 则静水也认。 */
    public static final boolean REQUIRE_FLOWING_WATER = true;
    /** 直接灌周边田的概率门（1/4 → 平均 4 秒补 1 格）。 */
    private static final int IRRIGATE_CHANCE = 4;

    private static final int[] BOTTOM_ROW = {6, 7, 8};

    public TongCheBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PART, CORE_PART)
                .setValue(PLANE, Direction.Axis.Z)
                .setValue(PLUGGED, false)
                .setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, PLANE, PLUGGED, ACTIVE);
    }

    // ==================== 3×3 摆位换算 ====================

    /** part → 横向偏移（-1/0/1）。 */
    public static int widthOffset(int part) {
        return part % 3 - 1;
    }

    /** part → 竖向偏移（+1/0/-1，part 0 在顶排）。 */
    public static int heightOffset(int part) {
        return 1 - part / 3;
    }

    /** part → 相对核心的坐标（轮面为 plane 时的横向轴）。 */
    public static BlockPos offsetOf(BlockPos core, Direction.Axis plane, int part) {
        int w = widthOffset(part);
        int h = heightOffset(part);
        return plane == Direction.Axis.X ? core.offset(0, h, w) : core.offset(w, h, 0);
    }

    /** 由某个部件反推核心位置。 */
    public static BlockPos coreOf(BlockPos pos, Direction.Axis plane, int part) {
        int w = widthOffset(part);
        int h = heightOffset(part);
        return plane == Direction.Axis.X ? pos.offset(0, -h, -w) : pos.offset(-w, -h, 0);
    }

    // ==================== 放置：放一格 → 自动展开 3×3 ====================

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction.Axis plane = context.getHorizontalDirection().getAxis();
        for (int part = 0; part < 9; part++) {
            BlockPos p = offsetOf(pos, plane, part);
            if (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced()) {
                return null; // 地方不够，放不下这台大车
            }
        }
        return this.defaultBlockState().setValue(PLANE, plane);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        Direction.Axis plane = state.getValue(PLANE);
        BlockState part = state.setValue(PLUGGED, false).setValue(ACTIVE, false);
        for (int i = 0; i < 9; i++) {
            if (i == CORE_PART) {
                continue;
            }
            level.setBlock(offsetOf(pos, plane, i), part.setValue(PART, i), 3);
        }
        level.setBlock(pos, part.setValue(PART, CORE_PART), 3);
        level.scheduleTick(pos, this, INTERVAL);

        // 没水就先提个醒（车下须有激流，"堰陂障流"那一步）
        if (!(placer instanceof Player player) || hasDrivingWater(level, pos, plane)) {
            return;
        }
        player.displayClientMessage(Component.translatable("block.tiangongkaiwu.tong_che.need_water"), false);
    }

    // ==================== 核心结算 ====================

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != CORE_PART) {
            return; // 只有核心干活（部件不自己排 tick）
        }
        Direction.Axis plane = state.getValue(PLANE);
        boolean spinning = !state.getValue(PLUGGED) && hasDrivingWater(level, pos, plane);
        if (spinning != state.getValue(ACTIVE)) {
            setActive(level, pos, plane, spinning);
        }
        if (spinning) {
            if (random.nextInt(IRRIGATE_CHANCE) == 0) {
                irrigateAround(level, pos, plane);
            }
            if (random.nextInt(4) == 0) {
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.35F, 1.4F);
            }
        }
        level.scheduleTick(pos, this, INTERVAL);
    }

    /** 同步 9 格的 ACTIVE（梘与传动轴都靠它判断"这台车在转吗"）。 */
    private static void setActive(ServerLevel level, BlockPos core, Direction.Axis plane, boolean active) {
        for (int i = 0; i < 9; i++) {
            BlockPos p = offsetOf(core, plane, i);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof TongCheBlock && s.getValue(ACTIVE) != active) {
                level.setBlock(p, s.setValue(ACTIVE, active), 3);
            }
        }
    }

    /** 车下（底排三格及其正下方）有没有"能激轮"的水。 */
    public static boolean hasDrivingWater(Level level, BlockPos core, Direction.Axis plane) {
        for (int part : BOTTOM_ROW) {
            BlockPos p = offsetOf(core, plane, part);
            if (isDrivingWater(level.getFluidState(p)) || isDrivingWater(level.getFluidState(p.below()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDrivingWater(FluidState fluid) {
        if (!fluid.is(FluidTags.WATER)) {
            return false;
        }
        return !REQUIRE_FLOWING_WATER || !fluid.isSource();
    }

    /** 直接给挨着轮子的稻田补水（不经过梘也能用，梘的作用是把水引远）。 */
    private static void irrigateAround(ServerLevel level, BlockPos core, Direction.Axis plane) {
        for (int i = 0; i < 9; i++) {
            BlockPos p = offsetOf(core, plane, i);
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                waterCell(level, p.relative(dir));
            }
        }
    }

    private static void waterCell(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() == TiangongKaiwu.RICE_STALK.get()) {
            int moisture = state.getValue(RiceStalkBlock.MOISTURE);
            if (moisture < RiceStalkBlock.MAX_MOISTURE) {
                level.setBlock(pos, RiceStalkBlock.withMoisture(state, moisture + 1), 2);
            }
        } else if (state.getBlock() == TiangongKaiwu.RICE_STALK_DRY.get()) {
            level.setBlock(pos, RiceStalkBlockDry.reviveState(state), 3);
        }
    }

    // ==================== 动力输出（供传动轴读取） ====================

    /** 本格能发出多少动力（不在转就是 0）。 */
    public static int emittedPower(BlockState state) {
        return state.getValue(ACTIVE) ? POWER_OUTPUT : 0;
    }

    /** 这台车在转吗（供梘判断"上游有没有水"）。 */
    public static boolean isRunning(BlockState state) {
        return state.getValue(ACTIVE);
    }

    // ==================== 栓木碍止 ====================

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty()) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit); // 手里拿着东西 → 让原版流程去放方块
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        Direction.Axis plane = state.getValue(PLANE);
        BlockPos core = coreOf(pos, plane, state.getValue(PART));
        boolean plugging = !state.getValue(PLUGGED);
        for (int i = 0; i < 9; i++) {
            BlockPos p = offsetOf(core, plane, i);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof TongCheBlock) {
                level.setBlock(p, s.setValue(PLUGGED, plugging), 3);
            }
        }
        level.playSound(null, core, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, plugging ? 0.7F : 1.2F);
        player.displayClientMessage(Component.translatable(plugging
                ? "block.tiangongkaiwu.tong_che.plugged"
                : "block.tiangongkaiwu.tong_che.unplugged"), true);
        level.scheduleTick(core, this, 1);
        return ItemInteractionResult.sidedSuccess(false);
    }

    // ==================== 拆装 ====================

    /** 挖任意一格 → 整台收回，只掉一个物品。 */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        Direction.Axis plane = state.getValue(PLANE);
        BlockPos core = coreOf(pos, plane, state.getValue(PART));
        for (int i = 0; i < 9; i++) {
            BlockPos p = offsetOf(core, plane, i);
            if (level.getBlockState(p).getBlock() instanceof TongCheBlock) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // 掉在**被挖的那一格**（原先固定掉在车中心 core，挖边缘格时物品"跑"到中间，很怪）
        popResource(level, pos, new ItemStack(TiangongKaiwu.TONG_CHE_ITEM.get()));
    }

    /** 部件成了"无主孤块"（核心不在）→ 自毁，不掉落。 */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide) {
            return;
        }
        Direction.Axis plane = state.getValue(PLANE);
        BlockPos core = coreOf(pos, plane, state.getValue(PART));
        BlockState coreState = level.getBlockState(core);
        boolean coreOk = coreState.getBlock() instanceof TongCheBlock
                && coreState.getValue(PART) == CORE_PART;
        if (!coreOk) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }
    // ====== 碰撞箱（2026-09-20 补：原先 noCollission 导致玩家能穿过整套装置，
    // 踏车更是站不上去、永远转不起来）======
    /** 筒车（整台大轮是实体结构：满格碰撞，可站上去、不能穿过） */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                       CollisionContext context) {
        return Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    }
}
