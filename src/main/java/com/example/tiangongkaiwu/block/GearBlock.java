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
 * 牙轮：90° 变向节——让两根**互相垂直**的轴之间能传劲（2026-09-27 拍板）。
 *
 * <p>书源：王祯《农书》水磨「在轮轴上安装一个齿轮，和磨轴下部平装的一个齿轮相衔接」——
 * 立轴的劲转向 90° 带动卧轴磨盘；水转翻车「上面卧轮齿与竖轮轮齿咬接」同理。
 * 古人木齿轮的齿称「牙」，故名牙轮。
 *
 * <p>规则（§10.6.3）：牙轮**只连互相垂直的方向，不直通**——直通请用传动杆。
 * 于是：拐弯必须经过牙轮（网络几何有意义），两条产线天然分开。
 * 劲传播的具体判定在 {@link PowerNetwork}：从牙轮进入方向垂直的四个方向传出。
 *
 * <p>{@code AXIS} 只是外观（轮盘垂直于哪个轴），不影响传播——传播只看进入方向。
 */
public class GearBlock extends Block {

    /** 轮盘轴向外观的朝向（轮面垂直于它）。 */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    /** 本格所在网络的劲总量（贴图用：铜箍亮不亮）。 */
    public static final IntegerProperty POWER = ShaftBlock.POWER;

    public static final int INTERVAL = ShaftBlock.INTERVAL;
    public static final int REACT_DELAY = ShaftBlock.REACT_DELAY;

    public GearBlock(Properties properties) {
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
     * 放置朝向：装在轴端（点的面与轴同向）→ 跟那根杆同轴（轮面垂直于杆，正是「竖轮装在横轴上」）；
     * 其它情况按点击面。
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction.Axis axis = face.getAxis();
        BlockState behind = context.getLevel()
                .getBlockState(context.getClickedPos().relative(face.getOpposite()));
        if (behind.getBlock() instanceof ShaftBlock
                && behind.getValue(ShaftBlock.AXIS) == axis) {
            return this.defaultBlockState().setValue(AXIS, axis);
        }
        return this.defaultBlockState().setValue(AXIS, axis);
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
        int power = Math.min(PowerNetwork.networkPower(level, pos), ShaftBlock.MAX_POWER);
        if (power != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, power), 2);
        }
        level.scheduleTick(pos, this, INTERVAL);
    }

    /** 挖掉掉自己。 */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        popResource(level, pos, new ItemStack(TiangongKaiwu.YA_LUN_ITEM.get()));
    }

    /** 牙轮（矮台碰撞，能踩过去）。 */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                       CollisionContext context) {
        return Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D);
    }
}
