package com.example.tiangongkaiwu.block;

import com.example.tiangongkaiwu.TiangongKaiwu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 风帆车。书里水利节：「揚郡以**風帆**數扇，**俟風轉車，風息則止**。此車為**救潦**，
 * 欲**去澤水以便栽種**。蓋**去水非取水也，不適濟旱**。」
 *
 * <p>这是水利里**唯一一件"倒着干"的装置**——别的都是把水弄进田里，它专门把水弄走：
 * <ul>
 *   <li><b>只有下雨才转</b>（书："俟风转车，风息则止"，用降雨代替风）。</li>
 *   <li>每约 3 秒**抽掉半径内最近的一格水源**，水里泡着的沼泽、湖泽会一格一格见底
 *       —— 正是"去澤水以便栽種"，把不能种的涝地变成干田。</li>
 *   <li>**对旱田毫无用处**：它不发动力、不给田补水，也**不会喂梘**
 *       （故意不登记进 {@link WaterDevices#isRunning}）——书里那句"不適濟旱"就是这么落到机制上的。</li>
 * </ul>
 *
 * <p>它只抽**真正的水方块**（且只抽水源），不会去动水培稻那种"泡在方块里的虚拟水"，
 * 所以放在自家稻田边上不会把禾苗抽死（但会抽干你挖的引水沟——这就是"去水"，得离渠系远点放）。
 */
public class FengFanBlock extends Block {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 是否在转（照书：下雨才转）。 */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /** 结算间隔：每 20 tick（1 秒）。 */
    public static final int INTERVAL = 20;
    /** 排水概率分母：1/3 → 平均 60 tick（3 秒）抽掉一格水。 */
    private static final int DRAIN_CHANCE = 3;
    /** 排水半径（放太近会连着自家水渠一起抽，玩家得挑位置）。 */
    public static final int DRAIN_RADIUS = 5;

    public FengFanBlock(Properties properties) {
        super(properties);
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
        boolean running = level.isRaining();   // 俟風轉車，風息則止
        if (running != state.getValue(ACTIVE)) {
            level.setBlock(pos, state.setValue(ACTIVE, running), 3);
        }
        if (running && random.nextInt(DRAIN_CHANCE) == 0) {
            BlockPos drained = drainOne(level, pos);
            if (drained != null) {
                level.sendParticles(ParticleTypes.SPLASH,
                        drained.getX() + 0.5D, drained.getY() + 1.0D, drained.getZ() + 0.5D,
                        5, 0.3D, 0.2D, 0.3D, 0.0D);
                level.playSound(null, drained, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.3F, 0.8F);
            }
        }
        level.scheduleTick(pos, this, INTERVAL);
    }

    /** 抽掉半径内最近的一格水源，返回它的位置（没水可抽返回 null）。 */
    private static BlockPos drainOne(ServerLevel level, BlockPos center) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int radius = DRAIN_RADIUS;
        for (BlockPos p : BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            BlockState candidate = level.getBlockState(p);
            // 只认"真正的水方块"且只认水源：水培稻那类含水方块不在内，动不了禾苗
            if (candidate.getBlock() != Blocks.WATER || !candidate.getFluidState().isSource()) {
                continue;
            }
            double dx = p.getX() - center.getX();
            double dy = p.getY() - center.getY();
            double dz = p.getZ() - center.getZ();
            double dist = dx * dx + dy * dy + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                best = p.immutable();
            }
        }
        if (best == null) {
            return null;
        }
        level.setBlock(best, Blocks.AIR.defaultBlockState(), 3);
        return best;
    }

    /** 右键：把书里那句"不適濟旱"直接说给玩家听。 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty()) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        player.displayClientMessage(Component.translatable(level.isRaining()
                ? "block.tiangongkaiwu.feng_fan_che.spinning"
                : "block.tiangongkaiwu.feng_fan_che.how"), true);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, 1.2F);
        return ItemInteractionResult.sidedSuccess(false);
    }

    /** 挖掉掉自己。 */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        popResource(level, pos, new ItemStack(TiangongKaiwu.FENG_FAN_CHE_ITEM.get()));
    }
    // ====== 碰撞箱（2026-09-20 补：原先 noCollission 导致玩家能穿过整套装置，
    // 踏车更是站不上去、永远转不起来）======
    /** 风帆车（低位碰撞） */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                       CollisionContext context) {
        return Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D);
    }
}
