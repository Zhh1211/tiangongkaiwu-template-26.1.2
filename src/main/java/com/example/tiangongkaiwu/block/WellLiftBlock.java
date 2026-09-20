package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 井上小汲：**桔槔**与**辘轳**。书里水利节最后一句：
 * 「其淺池、小澮不載長車者……用**桔槔、轆轤**，功勞又甚細已。」
 *
 * <p>它们是全套水利里最原始的一档——**不需要动力网**（靠人力和配重），只要临着水就能立，
 * 慢得对得起书里那句「功勞又甚細已」：
 * <ul>
 *   <li><b>桔槔</b>：横杆配重，**无人也自己往复**，约 16 秒给邻田补一格田水。</li>
 *   <li><b>辘轳</b>：井上绞盘，**须有人立在旁边摇**（走开就停），约 8 秒一格——比桔槔快，仍远逊筒车（4 秒）。</li>
 * </ul>
 *
 * <p>所以进度感是：水桶（自己挑）→ 桔槔／辘轳（浅池边，慢）→ 拔车／踏车／牛车（要动力网）→ 筒车（垒堰引激流，昼夜不息）。
 * 两台井具也会喂**梘**（{@link WaterDevices#isRunning}），但**不输出动力**。
 */
public class WellLiftBlock extends Block {

    /** 两种井具。chanceDenominator 越大越慢（每次 20 tick 结算里提一格的概率是 1/它）。 */
    public enum Kind {
        JIE_GAO("jie_gao", 16, false),
        LU_LU("lu_lu", 8, true);

        public final String id;
        public final int chanceDenominator;
        /** 是否需要有人在旁操控（桔槔靠配重，不需要）。 */
        public final boolean needsOperator;

        Kind(String id, int chanceDenominator, boolean needsOperator) {
            this.id = id;
            this.chanceDenominator = chanceDenominator;
            this.needsOperator = needsOperator;
        }
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 是否在提水（也驱动动画贴图）。 */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /** 结算间隔：每 20 tick（1 秒）。 */
    public static final int INTERVAL = 20;
    /** 辘轳摇柄够得着的范围。 */
    public static final double OPERATOR_RADIUS = 4.0D;

    public final Kind kind;

    public WellLiftBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
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
        boolean running = hasWater(level, pos) && (!this.kind.needsOperator || hasOperator(level, pos));
        if (running != state.getValue(ACTIVE)) {
            level.setBlock(pos, state.setValue(ACTIVE, running), 3);
        }
        if (running && random.nextInt(this.kind.chanceDenominator) == 0) {
            WaterDevices.irrigateAround(level, pos);
            level.sendParticles(ParticleTypes.SPLASH,
                    pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 4, 0.3D, 0.1D, 0.3D, 0.0D);
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.25F, 1.6F);
        }
        level.scheduleTick(pos, this, INTERVAL);
    }

    /** 须临水而立：六邻（含自身那格）有一格水就算——书里的「淺池、小澮」都认。 */
    public static boolean hasWater(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (level.getFluidState(pos.relative(dir)).is(FluidTags.WATER)) {
                return true;
            }
        }
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    /** 有人在旁边摇（辘轳用）。 */
    private static boolean hasOperator(ServerLevel level, BlockPos pos) {
        AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D)
                .inflate(OPERATOR_RADIUS, 2.0D, OPERATOR_RADIUS);
        return !level.getEntitiesOfClass(Player.class, box).isEmpty();
    }

    /** 在提水吗（供梘判断上游有没有水）。 */
    public boolean isRunning(BlockState state) {
        return state.getValue(ACTIVE);
    }

    // ==================== 右键：问一句怎么用 ====================

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty()) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        player.displayClientMessage(Component.translatable(hasWater(level, pos)
                ? "block.tiangongkaiwu." + this.kind.id + ".how"
                : "block.tiangongkaiwu.well.need_water"), true);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, 1.0F);
        return ItemInteractionResult.sidedSuccess(false);
    }

    /** 挖掉掉自己。 */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        popResource(level, pos, new ItemStack(itemOf()));
    }

    private Item itemOf() {
        return this.kind == Kind.JIE_GAO
                ? TiangongKaiwu.JIE_GAO_ITEM.get()
                : TiangongKaiwu.LU_LU_ITEM.get();
    }
    // ====== 碰撞箱（2026-09-20 补：原先 noCollission 导致玩家能穿过整套装置，
    // 踏车更是站不上去、永远转不起来）======
    /** 桔槔 / 辘轳（低位碰撞） */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                       CollisionContext context) {
        return Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D);
    }
}
