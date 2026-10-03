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
public class ShaftBlock extends Block implements net.minecraft.world.level.block.EntityBlock {

    /** 本格所在网络的劲总量（0–255，显示用；实际分配走 PowerNetwork——真劲无上限，这只是显示属性域）。 */
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 255);
    /** 轴向：劲只沿它传播。 */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    /** 自调度间隔。 */
    public static final int INTERVAL = 10;
    /** 邻居变化后快速重算的延迟。 */
    public static final int REACT_DELAY = 2;
    public static final int MAX_POWER = 255;

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
     * 放置轴向：
     * <ul>
     * <li>点在某根杆的**端面**（面轴向 = 杆轴向）→ 顺着接着排；</li>
     * <li>其它情况 → 按点击面的轴向。</li>
     * </ul>
     * （2026-10-02 拍板：去掉「顺任意邻杆」和结算时的自动转向——
     * 传动杆不自动连接，横竖全由玩家摆；垂直变向请用 {@link GearBlock 牙轮}。）
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
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
            net.minecraft.core.BlockPos pos, BlockState state) {
        return new com.example.tiangongkaiwu.block.entity.ShaftBlockEntity(pos, state);
    }

    /** 静态模型交给 BER 旋转渲染（ENTITYBLOCK_ANIMATED = 本体不再画一遍）。 */
    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** 双端 ticker：每 10 tick 刷新网络三项指标缓存（Jade/红线只读缓存）。 */
    @Override
    public <T extends net.minecraft.world.level.block.entity.BlockEntity>
            net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
                    Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (type != TiangongKaiwu.SHAFT_BE_TYPE.get()) {
            return null;
        }
        return (lv, pos, st, be) -> ((com.example.tiangongkaiwu.block.entity.ShaftBlockEntity) be).refreshStats();
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // ① 旧档自愈：没有 BE（老存档的杆）→ setBlock 重建，BER 挂载点补上
        if (level.getBlockEntity(pos) == null) {
            level.setBlock(pos, state, 3);
        }

        // ② 劲显示：所在网络的劲总量（分配在机器端由 PowerNetwork 结算）
        int power = Math.min(PowerNetwork.networkPower(level, pos), MAX_POWER);
        if (power != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, power), 2);
        }
        level.scheduleTick(pos, this, INTERVAL);
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
