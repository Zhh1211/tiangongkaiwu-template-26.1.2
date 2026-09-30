package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 牙轮：90° 变向节——让两根**互相垂直**的轴之间能传劲（2026-09-27 拍板）。
 *
 * <p>书源：王祯《农书》水磨「在轮轴上安装一个齿轮，和磨轴下部平装的一个齿轮相衔接」、
 * 水转翻车「上面卧轮齿与竖轮轮齿咬接」。古人木齿轮的齿称「牙」，故名牙轮。
 *
 * <p><b>三乘三的大齿轮</b>（2026-09-30 用户反馈：单格塞不下、齿凸不出来；学 Create 的大齿轮）：
 * 放一格自动展开 3×3，挖任意一格整台收回、只掉一个。轮面立在竖直平面里，
 * {@code plane} 记旋转轴方向（x/z）；横放（卧轮）随批 2 的水车三向放置一起做。
 *
 * <p>规则不变：牙轮**只连互相垂直的方向，不直通**——直通请用传动杆。
 * 劲传播判定在 {@link PowerNetwork}（从进入方向垂直的四个方向传出），
 * 多方块的每一格都是齿轮导体，规则原样适用。
 *
 * <p>{@code ACTIVE} 供批 2 的旋转动画（BER）使用，当前仅同步不驱动外观。
 */
public class GearBlock extends Block {

    /** 部件索引 0–8（3×3 自左上起按行排；4 = 核心）。 */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 8);
    /** 旋转轴方向（轮面垂直于它）：x 或 z。 */
    public static final EnumProperty<Direction.Axis> PLANE =
            EnumProperty.create("plane", Direction.Axis.class, Direction.Axis.X, Direction.Axis.Z);
    /** 是否在转（批 2 BER 动画用）。 */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public static final int CORE_PART = 4;
    public static final int INTERVAL = 20;

    public GearBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PART, CORE_PART)
                .setValue(PLANE, Direction.Axis.Z)
                .setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, PLANE, ACTIVE);
    }

    // ==================== 3×3 摆位换算（与筒车同一套） ====================

    /** part → 横向偏移（-1/0/1）。 */
    public static int widthOffset(int part) {
        return part % 3 - 1;
    }

    /** part → 竖向偏移（+1/0/-1，part 0 在顶排）。 */
    public static int heightOffset(int part) {
        return 1 - part / 3;
    }

    /** part → 相对核心的坐标。 */
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
                return null; // 地方不够
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
        BlockState part = state.setValue(ACTIVE, false);
        for (int i = 0; i < 9; i++) {
            if (i == CORE_PART) {
                continue;
            }
            level.setBlock(offsetOf(pos, plane, i), part.setValue(PART, i), 3);
        }
        level.setBlock(pos, part.setValue(PART, CORE_PART), 3);
        level.scheduleTick(pos, this, INTERVAL);
    }

    // ==================== 结算（当前只保留自调度；批 2 接旋转动画） ====================

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != CORE_PART) {
            return;
        }
        // 劲网络是"问出来"的（机器端 BFS），齿轮没有自己的持续状态要结算；
        // 这里保留自调度，供批 2 的旋转动画接手。
        level.scheduleTick(pos, this, INTERVAL);
    }

    // ==================== 拆装 ====================

    /** 挖任意一格 → 整台收回，只掉一个物品（掉在被挖的那格）。 */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        Direction.Axis plane = state.getValue(PLANE);
        BlockPos core = coreOf(pos, plane, state.getValue(PART));
        for (int i = 0; i < 9; i++) {
            BlockPos p = offsetOf(core, plane, i);
            if (level.getBlockState(p).getBlock() instanceof GearBlock) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        popResource(level, pos, new ItemStack(TiangongKaiwu.YA_LUN_ITEM.get()));
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
        boolean coreOk = coreState.getBlock() instanceof GearBlock
                && coreState.getValue(PART) == CORE_PART;
        if (!coreOk) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** 大齿轮是实体结构：满格碰撞（与筒车一致）。 */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                       CollisionContext context) {
        return Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    }
}
