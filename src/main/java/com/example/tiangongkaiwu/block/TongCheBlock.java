package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.block.entity.TongCheBlockEntity;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 筒车（水车）：书里「凡河濱有制筒車者，堰陂障流，繞於車下，激輪使轉，挽水入筒，
 * 一一傾於梘內，流入畝中。晝夜不息，百畝無憂（不用水時，栓木礙止）」。
 *
 * <p><b>单方块大轮</b>：一个方块，模型把整台 3×3 大轮越界画出去（Create 同款思路），
 * 轮子立在竖直平面里，`plane` 记轮面朝向（x/z）。
 *
 * <p><b>要激流才转</b>：只有车下一排（轮子触水那排及其下方）有**流动的水**（非水源）时才转——
 * 所以玩家得自己在河滨垒堰，"堰陂障流"，把水逼到车下。静水塘里它不转。
 *
 * <p><b>两件事</b>：① 提水灌田——挨着的梘会从它这里"接到水"，由梘把水引到田里；
 * ② 输出动力 12 点给传动轴（书里效率刻度最高的装置，"昼夜不息，百亩无忧"）。
 *
 * <p><b>栓木碍止</b>：空手右键可插/拔木栓——插上就停（动力与提水都停），拔掉复转。
 */
public class TongCheBlock extends Block implements net.minecraft.world.level.block.EntityBlock {

    /** 轮面朝向（轴的方向）：x 或 z。 */
    public static final EnumProperty<Direction.Axis> PLANE =
            EnumProperty.create("plane", Direction.Axis.class, Direction.Axis.X, Direction.Axis.Z);
    /** 是否插了木栓（true = 停）。 */
    public static final BooleanProperty PLUGGED = BooleanProperty.create("plugged");
    /** 是否在转（水够 + 没插栓）。供梘与传动轴读取。 */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /** 动力输出（书：昼夜不息，百畝無憂 —— 四种动力源里最强）。 */
    public static final int POWER_OUTPUT = 12;
    /** 结算间隔：每 20 tick（1 秒）一次。 */
    public static final int INTERVAL = 20;
    /** 是否严格要求"流动的水"（书要"激轮"）。调 false 则静水也认。 */
    public static final boolean REQUIRE_FLOWING_WATER = true;
    /** 直接灌周边田的概率门（1/4 → 平均 4 秒补 1 格）。 */
    private static final int IRRIGATE_CHANCE = 4;

        @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
            net.minecraft.core.BlockPos pos, BlockState state) {
        return new TongCheBlockEntity(pos, state);
    }

    /** 静态模型交给 BER 旋转渲染（ENTITYBLOCK_ANIMATED = 本体不再画一遍）。 */
    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED;
    }

public TongCheBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PLANE, Direction.Axis.Z)
                .setValue(PLUGGED, false)
                .setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PLANE, PLUGGED, ACTIVE);
    }

    // ==================== 放置 ====================

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis plane = context.getHorizontalDirection().getAxis();
        return this.defaultBlockState().setValue(PLANE, plane);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        level.scheduleTick(pos, this, INTERVAL);

        // 没水就先提个醒（车下须有激流，"堰陂障流"那一步）
        if (!(placer instanceof Player player) || hasDrivingWater(level, pos, state.getValue(PLANE))) {
            return;
        }
        player.displayClientMessage(Component.translatable("block.tiangongkaiwu.tong_che.need_water"), false);
    }

    // ==================== 核心结算 ====================

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction.Axis plane = state.getValue(PLANE);
        boolean spinning = !state.getValue(PLUGGED) && hasDrivingWater(level, pos, plane);
        if (spinning != state.getValue(ACTIVE)) {
            level.setBlock(pos, state.setValue(ACTIVE, spinning), 3);
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

    /** 轮子触水那排（横向三格及其正下方）有没有"能激轮"的水。 */
    public static boolean hasDrivingWater(Level level, BlockPos pos, Direction.Axis plane) {
        for (int w = -1; w <= 1; w++) {
            BlockPos p = plane == Direction.Axis.X ? pos.offset(0, -1, w) : pos.offset(w, -1, 0);
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
    private static void irrigateAround(ServerLevel level, BlockPos pos, Direction.Axis plane) {
        for (int w = -1; w <= 1; w++) {
            for (int h = -1; h <= 1; h++) {
                BlockPos p = plane == Direction.Axis.X ? pos.offset(0, h, w) : pos.offset(w, h, 0);
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    waterCell(level, p.relative(dir));
                }
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
        boolean plugging = !state.getValue(PLUGGED);
        level.setBlock(pos, state.setValue(PLUGGED, plugging), 3);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, plugging ? 0.7F : 1.2F);
        player.displayClientMessage(Component.translatable(plugging
                ? "block.tiangongkaiwu.tong_che.plugged"
                : "block.tiangongkaiwu.tong_che.unplugged"), true);
        level.scheduleTick(pos, this, 1);
        return ItemInteractionResult.sidedSuccess(false);
    }

    // ==================== 掉落（代码直弹，项目惯例） ====================

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        popResource(level, pos, new ItemStack(TiangongKaiwu.TONG_CHE_ITEM.get()));
    }

    // ====== 碰撞箱（大轮是实体结构：满格碰撞，可站上去、不能穿过） ======
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    }
}
