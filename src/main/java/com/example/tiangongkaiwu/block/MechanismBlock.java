package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.block.entity.MechanismBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 机构方块（D15）：装置成型后替换掉原方块的「通用运动件」。
 *
 * <p>按**运动语义**分三种，不按机器分——新机器零新增方块，只写结构模板和动画参数：
 * <ul>
 * <li>{@link Semantic#SLIDER} 往复件：碓杵（上下舂）；</li>
 * <li>{@link Semantic#PIVOT} 枢轴件：桔槔杠杆（绕支点摆，后续批次）；</li>
 * <li>{@link Semantic#ROTOR} 转轮件：辘轳轮（自转，后续批次）。</li>
 * </ul>
 *
 * <p>本体不渲染（{@code ENTITYBLOCK_ANIMATED}），外观由 BER 按语义画：
 * 碓杵画杵体并随核心 ACTIVE 上下舂；将来枢轴/转轮件画玩家自己的木料（材质采样）。
 * 碓的杵位恒在核心正上方，BER 直接向下找核心读 ACTIVE。
 */
public class MechanismBlock extends Block implements EntityBlock {

    /** 运动语义。 */
    public enum Semantic implements net.minecraft.util.StringRepresentable {
        SLIDER("slider"), PIVOT("pivot"), ROTOR("rotor");

        public static final net.minecraft.util.StringRepresentable.EnumCodec<Semantic> CODEC =
                net.minecraft.util.StringRepresentable.fromEnum(Semantic::values);

        private final String name;

        Semantic(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static final EnumProperty<Semantic> SEMANTIC = EnumProperty.create("semantic", Semantic.class);

    public MechanismBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(SEMANTIC, Semantic.SLIDER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SEMANTIC);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MechanismBlockEntity(pos, state);
    }

    /** 本体不画，交给 BER。 */
    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** 机构件细长，不挡人。 */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    /** 拆机构件：弹出记录的原方块，核心退回未成型态（还原）。 */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            unformCore(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** 核心不在正下方（被挖/被换/未成型）→ 机构件自毁并吐出记录的原方块。 */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide) {
            return;
        }
        BlockState below = level.getBlockState(pos.below());
        if (!(below.getBlock() instanceof DuiBlock) || !below.getValue(DuiBlock.FORMED)) {
            popAndRemove(level, pos);
        }
    }

    /** 核心退回未成型态（杵位恒在核心正上方）。 */
    private static void unformCore(Level level, BlockPos mechanismPos) {
        BlockPos core = mechanismPos.below();
        BlockState coreState = level.getBlockState(core);
        if (coreState.getBlock() instanceof DuiBlock && coreState.getValue(DuiBlock.FORMED)) {
            level.setBlock(core, coreState.setValue(DuiBlock.FORMED, false), 3);
        }
    }

    /** 弹出记录的原方块（一次性：弹完清零防重复），并移除机构方块。 */
    public static void popAndRemove(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MechanismBlockEntity mbe && !mbe.original.isAir()) {
            popResource(level, pos, new ItemStack(mbe.original.getBlock()));
            mbe.original = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        level.removeBlock(pos, false);
    }
}
