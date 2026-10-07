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
 * <p><b>要激流才转（flowScore 门槛判据，D13 + P0-3）</b>：轮子触水下沿 3 格至少 2 格是
 * 流动水（非水源），且动量切向投影和过门槛——**静水塘不转，挖一格河岸也不转**；只有
 * 垒堰收窄河道逼出连续激流才转。过门槛即恒速恒劲，不随流速涨落（要产量堆数量）。
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
    /** flowScore 门槛：触水格动量切向分量之和 ≥ 此值即「激轮」成功（静水=0 不过）。 */
    public static final double FLOW_SCORE_MIN = 0.015D;
    /** 堰陂障流（P0-3）：触水下沿 3 格至少要有几格流动水（非水源）才算激流。 */
    public static final int MIN_FLOWING_CELLS = 2;
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
        if (!(placer instanceof Player player) || flowScore(level, pos, state.getValue(PLANE)) >= FLOW_SCORE_MIN) {
            return;
        }
        player.displayClientMessage(Component.translatable("block.tiangongkaiwu.tong_che.need_water"), false);
    }

    // ==================== 核心结算 ====================

    /** 旧档自愈入口（3c）：老存档的杆/车没有 pending tick，靠 random tick 唤醒。 */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 旧档自愈：没有 BE（BE 引入前的老车）→ setBlock 重建，BER 挂载点补上
        if (level.getBlockEntity(pos) == null) {
            level.setBlock(pos, state, 3);
        }
        Direction.Axis plane = state.getValue(PLANE);
        boolean spinning = !state.getValue(PLUGGED) && flowScore(level, pos, plane) >= FLOW_SCORE_MIN;
        if (spinning != state.getValue(ACTIVE)) {
            level.setBlock(pos, state.setValue(ACTIVE, spinning), 3);
        }
        if (spinning) {
            if (random.nextInt(IRRIGATE_CHANCE) == 0) {
                irrigateAround(level, pos, plane);
            }
            if (random.nextInt(4) == 0) {
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.35F, 1.4F);
                // 辅助反馈：轮下溅水花（轮底那排水格上方起溅）
                for (int w = -1; w <= 1; w += 2) {
                    BlockPos p = plane == Direction.Axis.X ? pos.offset(0, -1, w) : pos.offset(w, -1, 0);
                    if (level.getFluidState(p).is(FluidTags.WATER)) {
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                                p.getX() + 0.5D, p.getY() + 0.9D, p.getZ() + 0.5D, 6, 0.2D, 0.1D, 0.2D, 0.0D);
                    }
                }
            }
        } else {
            // 引导反馈：轮触着静水但不转——偶起一圈涟漪，暗示"水是活的但流不动，去垒堰"
            if (random.nextInt(8) == 0 && touchesWater(level, pos, plane)) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                        pos.getX() + 0.5D, pos.getY() - 0.6D, pos.getZ() + 0.5D, 2, 0.4D, 0.0D, 0.4D, 0.0D);
            }
            // 吱呀木轴音效留待自定义音资源（不拿原版音效凑数）
        }
        level.scheduleTick(pos, this, INTERVAL);
    }

    /** 轮底那排水格是否触水（静水也算）。 */
    private static boolean touchesWater(Level level, BlockPos pos, Direction.Axis plane) {
        for (int w = -1; w <= 1; w++) {
            BlockPos p = plane == Direction.Axis.X ? pos.offset(0, -1, w) : pos.offset(w, -1, 0);
            if (level.getFluidState(p).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    /**
     * flowScore：轮子触水那排（横向三格及正下方）所有水的动量矢量，
     * 投影到轮底切向取绝对值求和。静水塘=0；有流即过门槛（方向不问，轮正转反转都能被水推）。
     *
     * <p><b>堰陂障流硬门槛（P0-3）</b>：触水下沿那 3 格中至少 2 格必须是**流动水**
     * （非水源）——静水塘 0 格、挖一格河岸的散流 1 格，都过不去；只有垒堰收窄河道、
     * 逼出连续激流才转（书：「堰陂障流，激轮使转」）。原版水机制天然支持，无需新方块。
     *
     * <p>切向推导：轮面在竖直平面里绕 PLANE 轴转——plane=Z（轮面 XY）轮底切向是 X；
     * plane=X（轮面 ZY）轮底切向是 Z。
     */
    public static double flowScore(Level level, BlockPos pos, Direction.Axis plane) {
        int flowingCells = 0;
        for (int w = -1; w <= 1; w++) {
            BlockPos p = plane == Direction.Axis.X ? pos.offset(0, -1, w) : pos.offset(w, -1, 0);
            FluidState fluid = level.getFluidState(p);
            if (fluid.is(FluidTags.WATER) && !fluid.isSource()) {
                flowingCells++;
            }
        }
        if (flowingCells < MIN_FLOWING_CELLS) {
            return 0.0D;
        }
        double score = 0.0D;
        for (int w = -1; w <= 1; w++) {
            BlockPos base = plane == Direction.Axis.X ? pos.offset(0, -1, w) : pos.offset(w, -1, 0);
            for (BlockPos p : new BlockPos[] {base, base.below()}) {
                FluidState fluid = level.getFluidState(p);
                if (!fluid.is(FluidTags.WATER)) {
                    continue;
                }
                net.minecraft.world.phys.Vec3 flow = fluid.getFlow(level, p);
                score += plane == Direction.Axis.X ? Math.abs(flow.z()) : Math.abs(flow.x());
            }
        }
        return score;
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
