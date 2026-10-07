package com.example.tiangongkaiwu.item;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.block.GearBlock;
import com.example.tiangongkaiwu.block.JianBlock;
import com.example.tiangongkaiwu.block.MechanismBlock;
import com.example.tiangongkaiwu.block.ShaftBlock;
import com.example.tiangongkaiwu.block.entity.MechanismBlockEntity;
import com.example.tiangongkaiwu.multiblock.MultiBlockTemplates;
import com.example.tiangongkaiwu.multiblock.StructureTemplate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.function.Predicate;

/**
 * 墨斗 v2（2026-10-04 拍板）：木工弹线定型的器具，一身四职——
 *
 * <ol>
 * <li><b>装配钥匙</b>：右键未成型机器核心（碓=臼位）→ 查模板（{@link MultiBlockTemplates}，
 *     来自结构方块 .nbt 编译）四向旋转匹配 → 成功「弹墨线」成型；再点已成型核心 → 拆线还原。</li>
 * <li><b>自动补料</b>：结构不齐时**潜行右键** → 从背包扣料把缺的木料/料口/传动轴补进世界
 *     （放齐直接成型）；扣不齐照旧报缺件明细。普通右键只报缺件不扣料。
 *     创造模式不扣背包。</li>
 * <li><b>扳手</b>：右键传动杆/小牙轮 → 轴向 x→y→z 轮转（轴统一用我们的轴）。</li>
 * <li><b>隔板</b>：右键梘侧面 → 该侧插/拆隔板，联动隔壁梘对侧（一块板隔两家）。</li>
 * </ol>
 */
public class MoDouItem extends Item {

    public MoDouItem(Properties properties) {
        super(properties);
    }

    /** 扳手轮转轴向：x→y→z→x。 */
    private static Direction.Axis nextAxis(Direction.Axis axis) {
        return switch (axis) {
            case X -> Direction.Axis.Y;
            case Y -> Direction.Axis.Z;
            case Z -> Direction.Axis.X;
        };
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        boolean client = level.isClientSide;

        // ---- 扳手：轴类轮转轴向 ----
        if (state.getBlock() instanceof ShaftBlock || state.getBlock() instanceof GearBlock) {
            if (client) {
                return InteractionResult.SUCCESS;
            }
            Direction.Axis next = nextAxis(state.getValue(BlockStateProperties.AXIS));
            level.setBlock(pos, state.setValue(BlockStateProperties.AXIS, next), 3);
            if (player != null) {
                player.displayClientMessage(Component.translatable("block.tiangongkaiwu.modou.wrenched"), true);
            }
            level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.5F, 1.2F);
            return InteractionResult.CONSUME;
        }

        // ---- 隔板：梘侧面插/拆（点顶/底面 = 朝玩家站的方向插）----
        if (state.getBlock() instanceof JianBlock) {
            if (client) {
                return InteractionResult.SUCCESS;
            }
            Direction side = context.getClickedFace().getAxis().isHorizontal()
                    ? context.getClickedFace()
                    : context.getHorizontalDirection();
            boolean sealing = !state.getValue(JianBlock.sealFor(side));
            JianBlock.toggleSeal((ServerLevel) level, pos, side);
            if (player != null) {
                player.displayClientMessage(Component.translatable(
                        sealing ? "block.tiangongkaiwu.modou.sealed" : "block.tiangongkaiwu.modou.unsealed"), true);
            }
            level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.6F,
                    sealing ? 0.9F : 1.3F);
            return InteractionResult.CONSUME;
        }

        // ---- 装配：机器核心成型 / 拆线（D15 泛化：碓/桔槔/辘轳……凡 Formable 皆认） ----
        if (!(state.getBlock() instanceof com.example.tiangongkaiwu.multiblock.Formable formable)) {
            return InteractionResult.PASS;
        }
        if (client) {
            return InteractionResult.SUCCESS;
        }
        StructureTemplate template = MultiBlockTemplates.get(formable.templateId());
        if (template == null) {
            return InteractionResult.PASS;
        }

        if (formable.isFormed(state)) {
            // 拆线还原
            formable.unformCore((ServerLevel) level, pos);
            if (player != null) {
                player.displayClientMessage(Component.translatable("block.tiangongkaiwu.modou.unformed"), true);
            }
            level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.6F, 0.8F);
            return InteractionResult.CONSUME;
        }

        int rot = template.matchAt(level, pos);
        if (rot < 0 && player != null && player.isShiftKeyDown()) {
            // 潜行右键 = 自动补料：按「最接近能成」的旋转，从背包扣料把缺件放进世界
            int fillRot = template.bestRotAt(level, pos);
            int filled = autoFill(template, (ServerLevel) level, pos, fillRot, player);
            if (filled > 0) {
                level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.7F, 1.0F);
                rot = template.matchAt(level, pos);
            }
        }

        if (rot < 0) {
            if (player != null) {
                int rot2 = template.bestRotAt(level, pos);
                int[] missing = template.missingAt(level, pos, rot2);
                player.displayClientMessage(Component.translatable(
                        "block.tiangongkaiwu.modou.missing",
                        missing[StructureTemplate.Role.WOOD.ordinal()],
                        missing[StructureTemplate.Role.FEED.ordinal()],
                        missing[StructureTemplate.Role.SHAFT_SOCKET.ordinal()]), true);
            }
            return InteractionResult.CONSUME;
        }

        // ---- 成型：优先匹配自动成型，但不自动变出方块——
        //      只换运动件（SLIDER/PIVOT/ROTOR 三种语义位），WOOD 框架保持原方块、
        //      FEED 料口不动、SHAFT_SOCKET 的杆保持原样（包壳由 ShaftRenderer 按邻接自动显示）。----
        for (StructureTemplate.Part part : template.parts()) {
            MechanismBlock.Semantic semantic = switch (part.role()) {
                case SLIDER -> MechanismBlock.Semantic.SLIDER;
                case PIVOT -> MechanismBlock.Semantic.PIVOT;
                case ROTOR -> MechanismBlock.Semantic.ROTOR;
                default -> null;
            };
            if (semantic == null) {
                continue;
            }
            BlockPos p = pos.offset(StructureTemplate.spin(part.pos(), rot));
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
            level.setBlock(pos, formable.withFormed(state, true), 3);
            level.setBlock(p, TiangongKaiwu.MECHANISM.get().defaultBlockState()
                    .setValue(MechanismBlock.SEMANTIC, semantic), 3);
            if (level.getBlockEntity(p) instanceof MechanismBlockEntity mbe) {
                mbe.original = old;
            }
        }
        if (!formable.isFormed(state)) {
            level.setBlock(pos, formable.withFormed(state, true), 3);
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable("block.tiangongkaiwu.modou.formed"), true);
        }
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 1.4F);
        return InteractionResult.CONSUME;
    }

    /**
     * 自动补料（潜行右键）：按模板把缺件从玩家背包扣料放进世界。
     *
     * <p>补料语义：只放**模板要求的位**（WOOD 位放背包里任意原木/木板，轴向顺着部件方向；
     * FEED 位放漏斗、朝向按模板；SHAFT_SOCKET 位放我们的传动杆、轴向按模板）。
     * 已被非木料占住的位跳过（不拆玩家的东西）。创造模式不扣背包（木料位统一放橡木）。
     *
     * @return 实际补上的件数
     */
    private static int autoFill(StructureTemplate template, ServerLevel level, BlockPos core,
                                int rot, Player player) {
        boolean instabuild = player.getAbilities().instabuild;
        int filled = 0;
        for (StructureTemplate.Part part : template.parts()) {
            StructureTemplate.Role role = part.role();
            if (role == StructureTemplate.Role.SLIDER) {
                continue; // 运动件成型时免费生成
            }
            if (template.checkPart(level, core, part, rot)) {
                continue; // 该位已合格
            }
            BlockPos p = core.offset(StructureTemplate.spin(part.pos(), rot));
            BlockState old = level.getBlockState(p);
            if (!old.isAir() && !StructureTemplate.isWoodish(old)) {
                continue; // 占位不是木料：不拆玩家摆的东西
            }

            BlockState ps = null;
            if (role == StructureTemplate.Role.WOOD) {
                ItemStack taken = instabuild ? null : takeOne(player, MoDouItem::isWoodItem);
                Block wood = taken != null ? Block.byItem(taken.getItem()) : Blocks.OAK_LOG;
                if (!StructureTemplate.isWoodish(wood.defaultBlockState())) {
                    continue;
                }
                ps = wood.defaultBlockState();
            } else {
                BlockState templateState = template.placementState(part, rot);
                if (templateState == null) {
                    continue;
                }
                ItemStack want = role == StructureTemplate.Role.SHAFT_SOCKET
                        ? new ItemStack(TiangongKaiwu.SHAFT_ITEM.get())
                        : new ItemStack(Items.HOPPER);
                ItemStack taken = instabuild ? null : takeOne(player, st -> ItemStack.isSameItem(st, want));
                if (taken == null && !instabuild) {
                    continue; // 背包没有这件，留着报缺件
                }
                ps = templateState;
            }

            // 木料的轴向顺着部件相对核心的方向（横梁放横、立柱放竖）
            if (ps.hasProperty(BlockStateProperties.AXIS)) {
                var off = StructureTemplate.spin(part.pos(), rot);
                int ax = Math.abs(off.getX());
                int ay = Math.abs(off.getY());
                int az = Math.abs(off.getZ());
                Direction.Axis axis = ax >= ay && ax >= az ? Direction.Axis.X
                        : ay >= az ? Direction.Axis.Y : Direction.Axis.Z;
                ps = ps.setValue(BlockStateProperties.AXIS, axis);
            }

            if (StructureTemplate.isWoodish(old)) {
                Block.dropResources(old, level, p);
            }
            level.setBlock(p, ps, 3);
            filled++;
        }
        return filled;
    }

    /** 木料位的背包判定：任意原木/木板的物品形态。 */
    private static boolean isWoodItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi
                && StructureTemplate.isWoodish(bi.getBlock().defaultBlockState());
    }

    /** 从背包扣 1 个匹配物品，没有返回 null。 */
    private static ItemStack takeOne(Player player, Predicate<ItemStack> match) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && match.test(st)) {
                ItemStack one = st.split(1);
                if (st.isEmpty()) {
                    inv.setItem(i, ItemStack.EMPTY);
                }
                inv.setChanged();
                return one;
            }
        }
        return null;
    }
}
