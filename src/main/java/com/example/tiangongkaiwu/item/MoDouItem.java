package com.example.tiangongkaiwu.item;

import java.util.ArrayList;
import java.util.List;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.block.DuiBlock;
import com.example.tiangongkaiwu.block.MechanismBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 墨斗（D15 成型工具）：木工弹线定型的器具，也是大型装置的「装配钥匙」。
 *
 * <p>右键**未成型的碓**（臼位）→ 以它为核心做结构模板匹配（四向旋转对称）：
 * 两柱一梁的 3×3 木架，中上为杵位、梁上留料口。匹配成功则「弹墨线」成型——
 * 杵位换装为往复机构方块（杵），核心进入成型态（只画臼），之后碓头随动力上下舂。
 * 对已成型的碓再点一次 → 拆线还原（杵位还原原方块，核心退出成型态）。
 *
 * <p>结构（rot=0，沿 X 的墙式，其余三向由旋转匹配）：
 * <pre>
 * y=3:   .  料  .        料口：空或容器（箱子/漏斗；成型后由此进料）
 * y=2:   W  W  W         梁（任意原木/木板）
 * y=1:   W  S  W         柱 + 杵位（空或木；木则替换并返还）
 * y=0:   W  C  W         柱 + 核心（碓）
 * </pre>
 */
public class MoDouItem extends Item {

    /** 木架位置（rot=0，沿 X；核心在 (0,0,0)）。 */
    private static final List<BlockPos> WOOD_OFFSETS = List.of(
            new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0),
            new BlockPos(-1, 1, 0), new BlockPos(1, 1, 0),
            new BlockPos(-1, 2, 0), new BlockPos(0, 2, 0), new BlockPos(1, 2, 0));
    /** 杵位（往复机构）。 */
    private static final BlockPos SLIDER_OFFSET = new BlockPos(0, 1, 0);
    /** 料口（成型后碓从这里进料）。 */
    private static final BlockPos FEED_OFFSET = new BlockPos(0, 3, 0);

    public MoDouItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos core = context.getClickedPos();
        BlockState coreState = level.getBlockState(core);
        if (!(coreState.getBlock() instanceof DuiBlock)) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (coreState.getValue(DuiBlock.FORMED)) {
            // 拆线还原
            DuiBlock.unform((ServerLevel) level, core);
            if (player != null) {
                player.displayClientMessage(Component.translatable("block.tiangongkaiwu.modou.unformed"), true);
            }
            level.playSound(null, core, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.6F, 0.8F);
            return InteractionResult.CONSUME;
        }
        for (int rot = 0; rot < 4; rot++) {
            if (tryForm((ServerLevel) level, core, rot)) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("block.tiangongkaiwu.modou.formed"), true);
                }
                level.playSound(null, core, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 1.4F);
                return InteractionResult.CONSUME;
            }
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable("block.tiangongkaiwu.modou.fail"), true);
        }
        return InteractionResult.CONSUME;
    }

    /** 四向对称的结构匹配：rot 为绕核心的 90° 旋转次数。 */
    private static boolean tryForm(ServerLevel level, BlockPos core, int rot) {
        List<BlockPos> woods = new ArrayList<>(WOOD_OFFSETS.size());
        for (BlockPos off : WOOD_OFFSETS) {
            woods.add(core.offset(spin(off, rot)));
        }
        BlockPos sliderPos = core.offset(spin(SLIDER_OFFSET, rot));
        BlockPos feedPos = core.offset(spin(FEED_OFFSET, rot));

        for (BlockPos p : woods) {
            if (!isWoodish(level.getBlockState(p))) {
                return false;
            }
        }
        // 料口：空或容器（成型后由此进料，别拿木头堵死）
        BlockState feedState = level.getBlockState(feedPos);
        if (!feedState.isAir() && !(feedState.getBlock() instanceof net.minecraft.world.Container)) {
            return false;
        }
        BlockState sliderState = level.getBlockState(sliderPos);
        boolean replacedWood = isWoodish(sliderState);
        if (!sliderState.isAir() && !replacedWood) {
            return false;
        }

        // ---- 成型 ----
        BlockState original = sliderState;
        if (replacedWood) {
            Block.dropResources(sliderState, level, sliderPos);
            level.removeBlock(sliderPos, false);
        }
        // 顺序：先立核心成型态，再放机构件（否则机构件的邻块自检会在成型前自毁）
        level.setBlock(core, level.getBlockState(core).setValue(DuiBlock.FORMED, true), 3);
        level.setBlock(sliderPos, TiangongKaiwu.MECHANISM.get().defaultBlockState()
                .setValue(MechanismBlock.SEMANTIC, MechanismBlock.Semantic.SLIDER), 3);
        if (level.getBlockEntity(sliderPos) instanceof com.example.tiangongkaiwu.block.entity.MechanismBlockEntity mbe) {
            mbe.original = original;
        }
        return true;
    }

    private static BlockPos spin(BlockPos off, int rot) {
        int x = off.getX();
        int z = off.getZ();
        for (int i = 0; i < rot; i++) {
            int nx = z;
            int nz = -x;
            x = nx;
            z = nz;
        }
        return new BlockPos(x, off.getY(), z);
    }

    /** 木料判定：任意原木或木板（D15：任意木料拼装）。 */
    private static boolean isWoodish(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS);
    }
}
