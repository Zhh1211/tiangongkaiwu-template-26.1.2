package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 传动轴：把动力源（筒车／牛车／踏车／拔车）的"劲"送到加工机器（将来：舂米臼、磨、砻）。
 * 动力源种类统一由 {@link WaterDevices} 认，这里不必逐个认识。
 *
 * <p>传播规则（水利 B 批定的动力模型）：本格的动力 = 邻格动力里最大的那个 − 1。
 * 也就是**每过一格衰减 1 点**，所以动力有射程：筒车 12 点送 12 格，牛车 10 点送 10 格，
 * 踏车 5 点、拔车 2 点（数值取自书里灌田效率刻度）。
 * 好处是纯局部规则——不需要方块实体、不需要网络统计，性能稳、不会互相打架。
 *
 * <p>power &gt; 0 时换"有劲"贴图，一眼看出哪一段通了、哪一段是断的。
 * 机器（后续批）只要贴着一段 power ≥ 需求的轴就能干活，于是"自动化"天然成立。
 */
public class ShaftBlock extends Block {

    /** 本格动力 0–15（同样也是"传到这里还剩多少劲"）。 */
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 15);
    /** 轴朝向，仅影响外观（连接判定不挑朝向，摆成一串就通）。 */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    /** 自己每隔多少 tick 重新结算一次动力。 */
    public static final int INTERVAL = 10;
    /** 邻居变化后快速重算的延迟。 */
    public static final int REACT_DELAY = 2;
    public static final int MAX_POWER = 15;

    public ShaftBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(POWER, 0)
                .setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER, AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, INTERVAL);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, REACT_DELAY);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int incoming = 0;
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighbor = level.getBlockState(neighborPos);
            int neighborPower;
            if (neighbor.getBlock() instanceof ShaftBlock) {
                neighborPower = neighbor.getValue(POWER);
            } else {
                neighborPower = WaterDevices.emittedPower(neighbor);
                if (neighborPower <= 0) {
                    continue;
                }
            }
            int delivered = neighborPower - 1;
            if (delivered > incoming) {
                incoming = delivered;
            }
        }
        if (incoming > MAX_POWER) {
            incoming = MAX_POWER;
        }
        if (incoming < 0) {
            incoming = 0;
        }

        if (incoming != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, incoming), 2);
            // 动力变了，让相邻轴也赶快重算（不然要等自己那一轮）
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.relative(dir);
                if (level.getBlockState(neighborPos).getBlock() instanceof ShaftBlock) {
                    level.scheduleTick(neighborPos, this, REACT_DELAY);
                }
            }
        }
        // 自调度：动力源开停、拆装都能被跟上
        level.scheduleTick(pos, this, INTERVAL);
    }

    /** 挖掉掉自己（不写战利品表，路径确定）。 */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        popResource(level, pos, new ItemStack(TiangongKaiwu.SHAFT_ITEM.get()));
    }
    // ====== 碰撞箱（2026-09-20 补：原先 noCollission 导致玩家能穿过整套装置，
    // 踏车更是站不上去、永远转不起来）======
    /** 传动轴（低位碰撞） */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                       CollisionContext context) {
        return Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D);
    }
}
