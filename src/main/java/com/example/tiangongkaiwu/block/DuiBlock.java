package com.example.tiangongkaiwu.block;

import java.util.Optional;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.recipe.PoundingRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 碓（舂米）。依据《天工開物·粹精·攻稻》：
 * 「凡稻去殼用礱，去膜用舂……**水碓主舂，則兼併礱功**」——所以碓一步到位：谷进、米与糠出。
 * 「不及則粗，太過則粉，精糧從此出焉」——**动力不足就舂不透**，出的是糙米。
 * 「既舂以後，皮膜成粉，名曰**細糠**」——副产物是细糠。
 * 「凡水碓……**引水成功，即筒車灌田同一制度也**」——碓就用同一套动力网驱动。
 * 「設臼多寡不一……**並列十臼無憂**」——一臼一格，想要几臼就摆几个，动力够就都转。
 *
 * <p><b>它自己不存东西，也没有方块实体</b>：料从**上方容器**（箱子／漏斗／投掷器）取，
 * 产物送**下方容器**，下方没有容器就弹到地上。于是「筒车转 → 轴送动力 → 漏斗喂谷 → 碓出米」
 * 这条全自动链不需要 GUI、不需要自建容器，仍然沿用 B/C 批那套 {@code scheduleTick} 局部规则。
 *
 * <p>相邻（六向）能拿到的最大动力决定两件事：<b>转不转</b>（=0 停），以及<b>舂不舂得透</b>
 * （配方里的 {@code min_power}）。轴上的动力本身已按「每过一格衰减 1」算好，这里直接读。
 */
public class DuiBlock extends Block {

    /** 碓头／木架朝向（放置时 = 玩家面朝方向）。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 是否正在舂（驱动 4 帧动画）。 */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /** 自调度间隔：每 10 tick 看一次动力与料。 */
    public static final int INTERVAL = 10;
    /** 动力／邻居变化后的快速重算延迟。 */
    public static final int REACT_DELAY = 2;
    /** 每次结算里"舂一下"的概率门（1/2 → 平均 20 tick 出一份，约 1 秒）。 */
    private static final int WORK_CHANCE = 2;

    public DuiBlock(Properties properties) {
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
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, REACT_DELAY);
        }
    }

    // ==================== 核心结算 ====================

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int power = powerAt(level, pos);
        boolean working = power > 0 && findWork(level, pos, power) != null;
        if (working != state.getValue(ACTIVE)) {
            level.setBlock(pos, state.setValue(ACTIVE, working), 3);
        }
        if (working && random.nextInt(WORK_CHANCE) == 0) {
            pound(level, pos, power);
            level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.3F, 1.6F);
        }
        level.scheduleTick(pos, this, INTERVAL);
    }

    /** 相邻六向能拿到的最大动力（轴已含衰减；动力源直接读它在不在转）。 */
    public static int powerAt(Level level, BlockPos pos) {
        int best = 0;
        for (Direction dir : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(dir));
            int power = neighbor.getBlock() instanceof ShaftBlock
                    ? neighbor.getValue(ShaftBlock.POWER)
                    : WaterDevices.emittedPower(neighbor);
            if (power > best) {
                best = power;
            }
        }
        return best;
    }

    /** 舂一下：从上方容器取 1 个料，按动力出成品或糙米。 */
    private void pound(ServerLevel level, BlockPos pos, int power) {
        Work work = findWork(level, pos, power);
        if (work == null) {
            return;
        }
        Container from = containerAt(level, pos.above());
        if (from == null) {
            return;
        }
        from.removeItem(work.slot(), 1);
        from.setChanged();

        PoundingRecipe recipe = work.recipe();
        if (power >= recipe.minPower()) {
            give(level, pos, recipe.result().copy());
            if (!recipe.byproduct().isEmpty()) {
                give(level, pos, recipe.byproduct().copy());
            }
        } else {
            give(level, pos, recipe.coarse().copy());
        }
    }

    /** 上方容器里有没有能舂的料（动力不足但有糙米出路也算能舂）。 */
    private Work findWork(ServerLevel level, BlockPos pos, int power) {
        Container from = containerAt(level, pos.above());
        if (from == null) {
            return null;
        }
        for (int slot = 0; slot < from.getContainerSize(); slot++) {
            ItemStack stack = from.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Optional<RecipeHolder<PoundingRecipe>> found = level.getRecipeManager()
                    .getRecipeFor(TiangongKaiwu.POUNDING_TYPE.get(), new SingleRecipeInput(stack), level);
            if (found.isEmpty()) {
                continue;
            }
            PoundingRecipe recipe = found.get().value();
            if (power >= recipe.minPower() || !recipe.coarse().isEmpty()) {
                return new Work(slot, recipe);
            }
        }
        return null;
    }

    /** 产物：下方有容器就放进去，没有就弹出来。 */
    private void give(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        Container to = containerAt(level, pos.below());
        if (to != null && insert(to, stack)) {
            to.setChanged();
            return;
        }
        popResource(level, pos.above(), stack);
    }

    private static Container containerAt(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof Container container ? container : null;
    }

    /** 先叠到同类上，再找空位。 */
    private static boolean insert(Container container, ItemStack stack) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack in = container.getItem(slot);
            if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, stack)
                    && in.getCount() < Math.min(container.getMaxStackSize(), in.getMaxStackSize())) {
                in.grow(stack.getCount());
                container.setItem(slot, in);
                return true;
            }
        }
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                container.setItem(slot, stack);
                return true;
            }
        }
        return false;
    }

    private record Work(int slot, PoundingRecipe recipe) {
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
        int power = powerAt(level, pos);
        Component message;
        if (power <= 0) {
            message = Component.translatable("block.tiangongkaiwu.dui.need_power");
        } else if (containerAt(level, pos.above()) == null) {
            message = Component.translatable("block.tiangongkaiwu.dui.need_container");
        } else {
            message = Component.translatable("block.tiangongkaiwu.dui.power", power);
        }
        player.displayClientMessage(message, true);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, 1.0F);
        return ItemInteractionResult.sidedSuccess(false);
    }

    /** 挖掉掉自己（不写战利品表，路径确定）。 */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        popResource(level, pos, new ItemStack(TiangongKaiwu.DUI_ITEM.get()));
    }
}
