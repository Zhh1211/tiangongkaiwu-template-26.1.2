package com.example.tiangongkaiwu.compat.jei;

import java.util.List;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.recipe.PoundingRecipe;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * JEI 可选集成（本 mod **不硬依赖** JEI：装了才显示"碓"的加工配方，没装毫无影响）。
 *
 * <p>分工：
 * <ul>
 *   <li><b>合成配方</b>（筒车／梘／轴／牛车／踏车／拔车／碓）全是原版 {@code crafting_shaped}，
 *       JEI 自带支持，本插件一个字都不用写。</li>
 *   <li><b>加工配方</b>（碓：谷→米＋细糠／不足出糙米）是本 mod 的自定义类型
 *       {@code tiangongkaiwu:pounding}，JEI 不认识，所以要注册一个自己的分类。</li>
 * </ul>
 *
 * <p>配方列表直接取客户端已同步的 {@code RecipeManager}，所以 datapack 改了配方 JEI 也会跟着变。
 */
@JeiPlugin
public class TiangongKaiwuJeiPlugin implements IModPlugin {

    /** 碓的加工配方在 JEI 里的类型（与 MC 的 RecipeType 无关，只是 JEI 分类的标识）。 */
    public static final RecipeType<PoundingRecipe> POUNDING =
            RecipeType.create(TiangongKaiwu.MODID, "pounding", PoundingRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(TiangongKaiwu.MODID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new PoundingRecipeCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        List<PoundingRecipe> recipes = minecraft.level.getRecipeManager()
                .getAllRecipesFor(TiangongKaiwu.POUNDING_TYPE.get())
                .stream()
                .map(RecipeHolder::value)
                .toList();
        registration.addRecipes(POUNDING, recipes);
    }

    /** 碓本体即催化剂：在 JEI 里对着"碓"按 R 就能看到它能加工什么。 */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(POUNDING, TiangongKaiwu.DUI_ITEM.get());
    }
}
