package com.example.tiangongkaiwu.item;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.block.DuiBlock;
import com.example.tiangongkaiwu.block.MechanismBlock;
import com.example.tiangongkaiwu.block.entity.MechanismBlockEntity;
import com.example.tiangongkaiwu.multiblock.MultiBlockTemplates;
import com.example.tiangongkaiwu.multiblock.StructureTemplate;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 墨斗（D15 成型工具）：木工弹线定型的器具，也是大型装置的「装配钥匙」。
 *
 * <p>右键**未成型的机器核心**（碓=臼位）→ 查模板注册表（{@link MultiBlockTemplates}，
 * 模板来自结构方块 .nbt 编译）做四向旋转匹配 → 成功则「弹墨线」成型；
 * 对已成型核心再点一次 → 拆线还原。
 *
 * <p>成型纪律：**只换运动件**（SLIDER 等）——WOOD 框架保持玩家原方块不动、FEED 料口不动
 * （容器玩家自选）、SHAFT_SOCKET 留待包壳接口批次换装。
 *
 * <p>v2 路线（已拍板待实施）：普通右键 5 秒内再点出缺件明细；潜行右键=背包可扣齐则自动
 * 补料+成型，扣不齐则虚影显示缺件（木白/料口黄/轴红三色）。
 */
public class MoDouItem extends Item {

    public MoDouItem(Properties properties) {
        super(properties);
    }

    /** 机器方块 → 模板 id。 */
    private static String templateFor(Block block) {
        if (block instanceof DuiBlock) {
            return "dui";
        }
        return null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos core = context.getClickedPos();
        BlockState coreState = level.getBlockState(core);
        String machine = templateFor(coreState.getBlock());
        if (machine == null) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        StructureTemplate template = MultiBlockTemplates.get(machine);
        if (template == null) {
            return InteractionResult.PASS;
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

        int rot = template.matchAt(level, core);
        if (rot < 0) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("block.tiangongkaiwu.modou.fail"), true);
            }
            return InteractionResult.CONSUME;
        }

        // ---- 成型 ----
        for (StructureTemplate.Part part : template.parts()) {
            if (part.role() != StructureTemplate.Role.SLIDER) {
                continue; // WOOD 保持玩家原方块 / FEED 不动 / SHAFT_SOCKET 待包壳批次
            }
            BlockPos p = core.offset(StructureTemplate.spin(part.pos(), rot));
            BlockState old = level.getBlockState(p);
            boolean replacedWood = StructureTemplate.isWoodish(old);
            if (!old.isAir() && !replacedWood) {
                continue;
            }
            if (replacedWood) {
                Block.dropResources(old, level, p);
                level.removeBlock(p, false);
            }
            // 顺序：先立核心成型态，再放机构方块（否则机构件的邻块自检会在成型前自毁）
            level.setBlock(core, coreState.setValue(DuiBlock.FORMED, true), 3);
            level.setBlock(p, TiangongKaiwu.MECHANISM.get().defaultBlockState()
                    .setValue(MechanismBlock.SEMANTIC, MechanismBlock.Semantic.SLIDER), 3);
            if (level.getBlockEntity(p) instanceof MechanismBlockEntity mbe) {
                mbe.original = old;
            }
        }
        if (!coreState.getValue(DuiBlock.FORMED)) {
            level.setBlock(core, coreState.setValue(DuiBlock.FORMED, true), 3);
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable("block.tiangongkaiwu.modou.formed"), true);
        }
        level.playSound(null, core, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 1.4F);
        return InteractionResult.CONSUME;
    }
}
