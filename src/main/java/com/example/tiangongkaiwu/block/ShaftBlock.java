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
 * 传动杆：把动力源（筒车／牛车／踏车／拔车）的「劲」送到加工机器（碓，将来磨/砻）。
 *
 * <p><b>2026-09-27 起：劲网络化（§10.6 拍板）</b>——
 * <ul>
 * <li><b>零损耗</b>：不再「每格 −1」，劲沿网处处等价；本格 POWER 显示的是**所在网络的劲总量**；</li>
 * <li><b>只沿自身轴向传播</b>：不再六向——垂直方向要变向，请用 {@link GearBlock 牙轮}；</li>
 * <li>分配（谁的机器分到多少劲）由 {@link PowerNetwork} 统一结算，机器端只管问。</li>
 * </ul>
 *
 * <p><b>自动成型</b>：放置时顺着邻杆的轴向接（修掉「横着摆一串结果根根竖立」的观感问题）；
 * 结算时若发现「自己轴向上没有邻杆、但别的方向恰有一根」，也自动转过去。
 */
public class ShaftBlock extends Block {

    /** 本格所在网络的劲总量（0–15，显示用；实际分配走 PowerNetwork）。 */
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 15);
    /** 轴向：劲只沿它传播。 */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    /** 自调度间隔。 */
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

    /**
     * 放置自动成型：
     * <ol>
     * <li>点在某根杆的**端面**（面轴向 = 杆轴向）→ 顺着接着排；</li>
     * <li>点在其它方块上 → 按点击面；</li>
     * <li>周围已有杆可引导时（任何方向有邻杆）→ 顺第一根邻杆的轴向。</li>
     * </ol>
     * （第 3 条是为了"先随手摆错、再补排"时后面的杆能跟上前面那根的方向。）
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        BlockState behind = level.getBlockState(pos.relative(face.getOpposite()));
        if (behind.getBlock() instanceof ShaftBlock
                && behind.getValue(AXIS) == face.getAxis()) {
            return this.defaultBlockState().setValue(AXIS, face.getAxis()); // 接着排
        }
        for (Direction dir : Direction.values()) {
            BlockState nb = level.getBlockState(pos.relative(dir));
            if (nb.getBlock() instanceof ShaftBlock) {
                return this.defaultBlockState().setValue(AXIS, nb.getValue(AXIS)); // 顺邻杆
            }
        }
        return this.defaultBlockState().setValue(AXIS, face.getAxis());
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
        // ① 自动重成型：自己轴向上没有邻杆，但恰好只有一个别的方向有邻杆 → 转过去
        BlockState formed = reformAxis(level, pos, state);
        if (formed != state) {
            state = formed;
            level.setBlock(pos, state, 2);
        }

        // ② 劲显示：所在网络的劲总量（分配在机器端由 PowerNetwork 结算）
        int power = Math.min(PowerNetwork.networkPower(level, pos), MAX_POWER);
        if (power != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, power), 2);
        }
        level.scheduleTick(pos, this, INTERVAL);
    }

    /**
     * 自动重成型：数一数六个方向的邻杆/牙轮分布——
     * 自己轴向上一个都没有、且恰好只有一个别的方向有 → 顺它转。
     * 多方向都有邻杆时不动作（歧义，尊重现状），避免来回摆。
     */
    private static BlockState reformAxis(ServerLevel level, BlockPos pos, BlockState state) {
        Direction.Axis axis = state.getValue(AXIS);
        Direction.Axis other = null;
        int others = 0;
        for (Direction dir : Direction.values()) {
            BlockState nb = level.getBlockState(pos.relative(dir));
            boolean conduit = nb.getBlock() instanceof ShaftBlock || nb.getBlock() instanceof GearBlock;
            if (!conduit) {
                continue;
            }
            if (dir.getAxis() == axis) {
                return state; // 自己轴上有邻杆，不动
            }
            other = dir.getAxis();
            others++;
        }
        if (others == 1 && other != null) {
            return state.setValue(AXIS, other);
        }
        return state;
    }

    /** 挖掉掉自己（不写战利品表，路径确定）。 */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        popResource(level, pos, new ItemStack(TiangongKaiwu.SHAFT_ITEM.get()));
    }

    /** 传动杆（低位碰撞）。 */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                       CollisionContext context) {
        return Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D);
    }
}
