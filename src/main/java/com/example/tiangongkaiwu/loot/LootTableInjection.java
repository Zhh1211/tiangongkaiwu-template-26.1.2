package com.example.tiangongkaiwu.loot;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.item.ResidualData;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.LootTableEvent;

import java.util.List;

/**
 * 把《残页》注进原版地物战利品箱——「残页 → 翰墨台翻译 → 解锁书页」三段闭环的起点。
 *
 * <p>书源对齐：残页是教程书的钥匙，按设计只从**探索**获得（防白做），
 * 所以不做合成、不进创造栏外的任何直接获取。
 *
 * <p>注入方式：监听战利品表加载，向典型地物箱子各加一个池——
 * 60% 概率掉 0–1 张残页，条目在乃粒卷 9 个解锁点里随机，
 * 组件 {@code tiangongkaiwu:residual} 固定为「未翻译、0 经验」。
 */
@EventBusSubscriber(modid = TiangongKaiwu.MODID)
public class LootTableInjection {

    /** 乃粒卷的 9 个解锁条目（残页上写的篇目）。 */
    private static final List<String> ENTRIES = List.of(
            "naili_do", "naili_ma", "naili_mai", "naili_shu", "naili_shuili",
            "naili_shuji", "naili_dao_gong", "naili_dao_yi", "naili_dao_zai");

    /** 注入目标：常见地物箱子。 */
    private static final List<ResourceLocation> TARGETS = List.of(
            BuiltInLootTables.SIMPLE_DUNGEON,
            BuiltInLootTables.ABANDONED_MINESHAFT,
            BuiltInLootTables.DESERT_PYRAMID,
            BuiltInLootTables.JUNGLE_TEMPLE,
            BuiltInLootTables.STRONGHOLD_CORRIDOR,
            BuiltInLootTables.SHIPWRECK_TREASURE,
            BuiltInLootTables.BURIED_TREASURE,
            BuiltInLootTables.IGLOO_CHEST);

    @SubscribeEvent
    public static void onLootTableLoad(LootTableEvent event) {
        ResourceLocation id = event.getName();
        if (!TARGETS.contains(id)) {
            return;
        }
        var pool = LootPool.lootPool()
                .setRolls(UniformGenerator.between(0, 1))
                .when(LootItemRandomChanceCondition.randomChance(0.6F))
                .build();
        var builder = pool;
        for (String entry : ENTRIES) {
            builder.add(LootItem.lootTableItem(TiangongKaiwu.CAN_YE.get())
                    .setWeight(1)
                    .apply(SetComponentsFunction.setComponents(DataComponentMap.builder()
                            .set(TiangongKaiwu.RESIDUAL.get(),
                                    new ResidualData("naili_juan/" + entry, false, 0))
                            .build())));
        }
        event.getLootTable().addPool(builder);
    }
}
