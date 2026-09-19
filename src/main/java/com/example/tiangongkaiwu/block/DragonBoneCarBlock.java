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
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 龙骨车（牛车／踏车／拔车）：书里「其湖池不流水，或以牛力轉盤，或聚數人踏轉。車身長者二丈，
 * 短者半之。其內用龍骨拴串板，關水逆流而上。大抵一人竟日之力，灌田五畝，而牛則倍之。」
 * 以及「其淺池、小澮不載長車者，則數尺之車，一人兩手疾轉，竟日之功可灌二畝而已。」
 *
 * <p>三者是同一台"龙骨车"的三种驱动，只差**谁来出力气**：
 * <ul>
 *   <li><b>牛车</b>（车身长）：近旁有**被拴绳系住**的牛 → 转，动力 <b>10</b>（牛则倍之）。</li>
 *   <li><b>踏车</b>（车身长）：有**人站在车上**踏转 → 转，动力 <b>5</b>（一人竟日五亩）。</li>
 *   <li><b>拔车</b>（数尺短车）：有**人立在车旁**摇转 → 转，动力 <b>2</b>（短车二亩而已）。</li>
 * </ul>
 *
 * <p>共同前提：**车须临水**（书里牛车／踏车正是用在"湖池不流水"处，静水就够，不必像筒车那样要激流）。
 *
 * <p>长车放一格会**自动展开成 3 格**（横铺、垂直于玩家朝向），挖任意一格整台收回、只掉一个。
 * 动力经由传动轴送出，接口与筒车完全一致（{@link WaterDevices}）。
 */
public class DragonBoneCarBlock extends Block {

    /** 三种驱动。length = 车身格数，power = 动力输出（数值取自书里灌田效率刻度）。 */
    public enum Kind {
        NIU("niu_che", 3, 10),
        TA("ta_che", 3, 5),
        BA("ba_che", 1, 2);

        public final String id;
        public final int length;
        public final int power;

        Kind(String id, int length, int power) {
            this.id = id;
            this.length = length;
            this.power = power;
        }
    }

    /** 部件索引（0/1/2；核心恒为 1，短车只用核心那一格）。 */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);
    /** 朝向：车身沿它的顺时针方向横铺，同时决定模型旋转。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 是否在转（核心算出后同步到各部件，供传动轴与梘读取）。 */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public static final int CORE_PART = 1;
    /** 结算间隔：每 20 tick（1 秒）一次。 */
    public static final int INTERVAL = 20;
    /** 拴牛生效半径（原版拴绳够得着的范围）。 */
    public static final double NIU_RADIUS = 8.0D;
    /** 拔车"有人在旁手摇"的判定半径。 */
    public static final double BA_RADIUS = 3.0D;

    public final Kind kind;

    public DragonBoneCarBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PART, CORE_PART)
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING, ACTIVE);
    }

    // ==================== 摆位换算 ====================

    /** 本车实际用到的部件（短车只有核心一格）。 */
    private int[] parts() {
        return this.kind.length <= 1 ? new int[]{CORE_PART} : new int[]{0, 1, 2};
    }

    /** 车身横铺方向：朝向的顺时针（玩家面朝北，车就东西向摆在眼前）。 */
    public static Direction spreadDir(Direction facing) {
        return facing.getClockWise();
    }

    public BlockPos offsetOf(BlockPos core, Direction facing, int part) {
        if (this.kind.length <= 1) {
            return core;
        }
        return core.relative(spreadDir(facing), part - CORE_PART);
    }

    public BlockPos coreOf(BlockPos pos, Direction facing, int part) {
        if (this.kind.length <= 1) {
            return pos;
        }
        return pos.relative(spreadDir(facing), CORE_PART - part);
    }

    // ==================== 放置：放一格 → 自动展开 ====================

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos core = context.getClickedPos();
        Direction facing = context.getHorizontalDirection();
        for (int part : parts()) {
            BlockPos p = offsetOf(core, facing, part);
            if (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced()) {
                return null; // 地方不够，放不下这台车
            }
        }
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (int part : parts()) {
            if (part != CORE_PART) {
                level.setBlock(offsetOf(pos, facing, part),
                        state.setValue(PART, part).setValue(ACTIVE, false), 3);
            }
        }
        level.setBlock(pos, state.setValue(PART, CORE_PART).setValue(ACTIVE, false), 3);
        level.scheduleTick(pos, this, INTERVAL);

        if (!(placer instanceof Player player)) {
            return;
        }
        // 放下时提一句：要么是"没水"，要么是"这台车该怎么驱动"
        player.displayClientMessage(Component.translatable(isNearWater(level, pos, facing)
                ? "block.tiangongkaiwu." + this.kind.id + ".how"
                : "block.tiangongkaiwu.car.need_water"), false);
    }

    // ==================== 核心结算 ====================

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != CORE_PART) {
            return; // 只有核心干活
        }
        Direction facing = state.getValue(FACING);
        boolean running = isNearWater(level, pos, facing) && isDriven(level, pos, facing);
        if (running != state.getValue(ACTIVE)) {
            setActive(level, pos, facing, running);
        }
        if (running && random.nextInt(4) == 0) {
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.25F, 1.5F);
        }
        level.scheduleTick(pos, this, INTERVAL);
    }

    /** 车须临水：车格自身或其六邻有一格水即可（静水、流水、水田都算）。 */
    public boolean isNearWater(Level level, BlockPos core, Direction facing) {
        for (int part : parts()) {
            BlockPos p = offsetOf(core, facing, part);
            if (level.getFluidState(p).is(FluidTags.WATER)) {
                return true;
            }
            for (Direction dir : Direction.values()) {
                if (level.getFluidState(p.relative(dir)).is(FluidTags.WATER)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 谁来出力气（三种车各不同，书里的三句话）。 */
    private boolean isDriven(ServerLevel level, BlockPos core, Direction facing) {
        return switch (this.kind) {
            case NIU -> hasLeashedCow(level, core, facing);
            case TA -> hasPlayerOnCar(level, core, facing);
            case BA -> hasPlayerBeside(level, core, facing);
        };
    }

    /** 牛车：近旁有被拴绳系住的牛（书：以牛力转盘）。 */
    private boolean hasLeashedCow(ServerLevel level, BlockPos core, Direction facing) {
        AABB box = carBox(core, facing).inflate(NIU_RADIUS);
        for (Cow cow : level.getEntitiesOfClass(Cow.class, box)) {
            if (cow.isLeashed()) {
                return true;
            }
        }
        return false;
    }

    /** 踏车：有人站在车身上（书：聚数人踏转）。 */
    private boolean hasPlayerOnCar(ServerLevel level, BlockPos core, Direction facing) {
        AABB box = carBox(core, facing).inflate(0.25D, 0.0D, 0.25D).expandTowards(0.0D, 1.6D, 0.0D);
        return !level.getEntitiesOfClass(Player.class, box).isEmpty();
    }

    /** 拔车：有人在车旁摇转（书：一人两手疾转）。 */
    private boolean hasPlayerBeside(ServerLevel level, BlockPos core, Direction facing) {
        AABB box = carBox(core, facing).inflate(BA_RADIUS, 2.0D, BA_RADIUS);
        return !level.getEntitiesOfClass(Player.class, box).isEmpty();
    }

    /** 整车占地的包围盒（供判定用）。 */
    private AABB carBox(BlockPos core, Direction facing) {
        double x0 = core.getX();
        double z0 = core.getZ();
        double x1 = x0 + 1.0D;
        double z1 = z0 + 1.0D;
        for (int part : parts()) {
            BlockPos p = offsetOf(core, facing, part);
            x0 = Math.min(x0, p.getX());
            z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX() + 1.0D);
            z1 = Math.max(z1, p.getZ() + 1.0D);
        }
        return new AABB(x0, core.getY(), z0, x1, core.getY() + 1.0D, z1);
    }

    /** 把"在转"同步给各部件。 */
    private void setActive(ServerLevel level, BlockPos core, Direction facing, boolean active) {
        for (int part : parts()) {
            BlockPos p = offsetOf(core, facing, part);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() == this && s.getValue(ACTIVE) != active) {
                level.setBlock(p, s.setValue(ACTIVE, active), 3);
            }
        }
    }

    // ==================== 动力输出（供传动轴／梘读取） ====================

    public int emittedPower(BlockState state) {
        return state.getValue(ACTIVE) ? this.kind.power : 0;
    }

    public boolean isRunning(BlockState state) {
        return state.getValue(ACTIVE);
    }

    // ==================== 右键：问一句"怎么驱动" ====================

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty()) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        Direction facing = state.getValue(FACING);
        BlockPos core = coreOf(pos, facing, state.getValue(PART));
        player.displayClientMessage(Component.translatable(isNearWater(level, core, facing)
                ? "block.tiangongkaiwu." + this.kind.id + ".how"
                : "block.tiangongkaiwu.car.need_water"), true);
        level.playSound(null, core, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, 1.1F);
        return ItemInteractionResult.sidedSuccess(false);
    }

    // ==================== 拆装 ====================

    /** 挖任意一格 → 整台收回，只掉一个物品。 */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        Direction facing = state.getValue(FACING);
        BlockPos core = coreOf(pos, facing, state.getValue(PART));
        for (int part : parts()) {
            BlockPos p = offsetOf(core, facing, part);
            if (level.getBlockState(p).getBlock() == this) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        popResource(level, core, new ItemStack(itemOf()));
    }

    private Item itemOf() {
        return switch (this.kind) {
            case NIU -> TiangongKaiwu.NIU_CHE_ITEM.get();
            case TA -> TiangongKaiwu.TA_CHE_ITEM.get();
            case BA -> TiangongKaiwu.BA_CHE_ITEM.get();
        };
    }

    /** 部件成了"无主孤块"（核心不在）→ 自毁，不掉落。 */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide || this.kind.length <= 1) {
            return;
        }
        Direction facing = state.getValue(FACING);
        int part = state.getValue(PART);
        if (part == CORE_PART) {
            return;
        }
        BlockPos core = coreOf(pos, facing, part);
        BlockState coreState = level.getBlockState(core);
        boolean coreOk = coreState.getBlock() == this && coreState.getValue(PART) == CORE_PART;
        if (!coreOk) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
