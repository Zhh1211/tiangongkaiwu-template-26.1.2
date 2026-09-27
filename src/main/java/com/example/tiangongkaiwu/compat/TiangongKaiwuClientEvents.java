package com.example.tiangongkaiwu.compat;

import com.example.tiangongkaiwu.TiangongKaiwu;
import net.minecraft.client.RecipeBookCategories;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRecipeBookCategoriesEvent;

/**
 * 客户端事件：给自定义配方类型一个「配方书类别」。
 *
 * <p>背景（2026-09-27 清理规格问题时发现）：我们的加工配方是自定义类型
 * {@code tiangongkaiwu:pounding}，原版没有它的配方书映射，于是客户端进世界时
 * {@code ClientRecipeBook} 会刷警告：
 * <pre>Unknown recipe category: tiangongkaiwu:pounding/tiangongkaiwu:pounding_rice</pre>
 * 原版代码走 {@code RecipeBookManager.findCategories(...)} 查找映射，找不到就打这条警告并
 * 回落到 {@code RecipeBookCategories.UNKNOWN}——**无害**，但属于日志噪音，且说明配方未归类。
 *
 * <p>修法用 NeoForge 官方钩子（无需 mixin）：监听 {@link RegisterRecipeBookCategoriesEvent}
 * （{@code IModBusEvent}，挂在 mod 事件总线上），为我们的配方类型注册一个 finder。
 *
 * <p>为什么归到 {@code CRAFTING_MISC}：碓是「加工」而非原版任何配方体系，
 * 归入"杂项合成"页签最贴近；且我们的配方没有解锁 advancement，
 * 配方书本来就不会主动显示它们——这里只是让它有个合法归属、不再报未知。
 *
 * <p>⚠️ 本类引用 {@code net.minecraft.client.*}，必须只在客户端加载：
 * 靠 {@code @EventBusSubscriber(value = Dist.CLIENT)} 限制；
 * 主类**不要** import 本类，否则服务端会因找不到客户端类而崩。
 *
 * <p>⚠️ 不写 {@code bus = ...}：NeoForge 21.1 已把 {@code EventBusSubscriber.Bus} 标记为待删除，
 * 现在由 NeoForge 按事件类型自动选择总线（本事件 {@code implements IModBusEvent} → mod 总线）。
 */
@EventBusSubscriber(modid = TiangongKaiwu.MODID, value = Dist.CLIENT)
public final class TiangongKaiwuClientEvents {

    private TiangongKaiwuClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterRecipeBookCategories(RegisterRecipeBookCategoriesEvent event) {
        event.registerRecipeCategoryFinder(
                TiangongKaiwu.POUNDING_TYPE.get(),
                holder -> RecipeBookCategories.CRAFTING_MISC);
    }
}
