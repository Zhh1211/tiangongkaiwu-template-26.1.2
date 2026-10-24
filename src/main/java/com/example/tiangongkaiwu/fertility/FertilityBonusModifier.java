package com.example.tiangongkaiwu.fertility;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.phys.Vec3;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * 地力增产（全局掉落修改器，「海关」）：作物掉落经过时结算熟地红利。
 *
 * <p>四步：
 * ① 被破坏的是作物吗（挂 crops 标签的方块：原版 minecraft:crops + 惯例 c:crops，主流 mod 作物都在）；
 * ② 从被破坏格<b>向下爬链</b>到第一个非作物格——两格水稻（穗→秆→土）与一切多格作物全覆盖，
 *    收最上面那格也好、断中间也好，总能爬到土；
 * ③ 那格地力 &gt; 0 → 掉落清单<b>每份物品追加「地力」份</b>同物；
 * ④ 只有直接坐在熟地上那格的掉落结算时地力 -1（两格作物一次收获不扣两次）。
 *
 * <p>挂在「掉落生成」而非「破坏事件」：手拆、机械收割、爆炸全走掉落管线，一个不漏；
 * 原版/别家代码零改动。
 */
public class FertilityBonusModifier extends LootModifier {

    /** 惯例作物标签（c:crops；NeoForge 21.1 常量类未收录，静态自建，标签没人加载时恒空无害）。 */
    private static final TagKey<Block> CONVENTIONAL_CROPS =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "crops"));

    public static final MapCodec<FertilityBonusModifier> CODEC =
            RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, FertilityBonusModifier::new));

    /** 作物爬链上限（覆盖多格高作物；正常的两格水稻用不满）。 */
    private static final int MAX_CROP_CHAIN = 4;

    public FertilityBonusModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        // ① 被破坏的是作物吗（非方块掉落如生物死亡，BLOCK_STATE 为空，直接放行）
        BlockState broken = ctx.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (broken == null || !isCrop(broken)) {
            return loot;
        }
        // ② 向下爬链找土
        Vec3 origin = ctx.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) {
            return loot;
        }
        ServerLevel level = ctx.getLevel();
        BlockPos breakPos = BlockPos.containing(origin.x, origin.y, origin.z);
        BlockPos soilPos = breakPos;
        for (int i = 0; i < MAX_CROP_CHAIN; i++) {
            BlockState below = level.getBlockState(soilPos.below());
            if (!isCrop(below)) {
                break;
            }
            soilPos = soilPos.below();
        }
        // ③ 熟地结算：掉落每份追加「地力」份
        int fertility = FertilityData.fertilityAt(level, soilPos);
        if (fertility <= 0) {
            return loot;
        }
        ObjectArrayList<ItemStack> out = new ObjectArrayList<>(loot);
        for (ItemStack stack : loot) {
            if (!stack.isEmpty()) {
                ItemStack bonus = stack.copy();
                bonus.setCount(Math.min(bonus.getMaxStackSize(), fertility));
                out.add(bonus);
            }
        }
        // ④ 耗地：只有直接坐在熟地上那格结算时扣（穗自毁不重复扣）
        if (soilPos.equals(breakPos.below())) {
            FertilityData.add(level, soilPos, -1);
        }
        return out;
    }

    private static boolean isCrop(BlockState state) {
        return state.is(BlockTags.CROPS) || state.is(CONVENTIONAL_CROPS);
    }
}
