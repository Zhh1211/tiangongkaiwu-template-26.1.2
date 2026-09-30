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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 牙轮：90° 变向节——让两根**互相垂直**的轴之间能传劲（2026-09-27 拍板）。
 *
 * <p>书源：王祯《农书》水磨「在轮轴上安装一个齿轮，和磨轴下部平装的一个齿轮相衔接」、
 * 水转翻车「上面卧轮齿与竖轮轮齿咬接」。古人木齿轮的齿称「牙」，故名牙轮。
 *
 * <p><b>Create 式大齿轮（2026-09-30 学 Create）</b>：**单方块 + 越界模型**——
 * 原版方块模型的元素允许 -16..32（正好 3 格范围），大齿轮的视觉直接画出去，
 * 不需要多方块展开/收回那套逻辑（研读结论：Create 的大齿轮、小水车都是这个路子，
 * 真多方块只有需要内部空间的粉碎轮）。破坏 = 挖中心一格。
 *
 * <p>规则不变：牙轮**只连互相垂直的方向，不直通**——直通请用传动杆。
 * 劲传播判定在 {@link PowerNetwork}（从进入方向垂直的四个方向传出）。
 * {@code AXIS} 三向（含横放卧轮），{@code ACTIVE} 供批 2 的旋转动画（BER）。
 */
public class GearBlock extends Block {

    /** 旋转轴方向（轮面垂直于它）：x / y / z 三向——横放卧轮免费送。 */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    /** 是否在转（批 2 BER 动画用）。 */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public static final int INTERVAL = 20;

    public GearBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AXIS, Direction.Axis.Z)
                .setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, ACTIVE);
    }

    /** 旋转轴 = 与点击面平行的轴（贴墙放立轮、放地上成卧轮），Create 大齿轮同款。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, INTERVAL);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 劲网络是"问出来"的（机器端 BFS），齿轮无持续状态；自调度留给批 2 旋转动画。
        level.scheduleTick(pos, this, INTERVAL);
    }

    /** 挖掉掉自己（单方块）。 */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        popResource(level, pos, new ItemStack(TiangongKaiwu.YA_LUN_ITEM.get()));
    }

    /**
     * 碰撞：轮盘中带一整片（轮面方向满格、轴向 6px 中带），越界部分无碰撞（与 Create 大齿轮观感一致）。
     * 轮面在垂直于 AXIS 的平面里；这里按 axis 给出中心格的中带盒。
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                       CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> Block.box(5.0D, 0.0D, 0.0D, 11.0D, 16.0D, 16.0D);
            case Y -> Block.box(0.0D, 5.0D, 0.0D, 16.0D, 11.0D, 16.0D);
            case Z -> Block.box(0.0D, 0.0D, 5.0D, 16.0D, 16.0D, 11.0D);
        };
    }
}
