package com.example.tiangongkaiwu.compat.jei;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.recipe.PoundingRecipe;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import net.minecraft.network.chat.Component;

/**
 * JEI 里"碓（舂米）"这一页：左边谷，箭头右边白米＋细糠，下一行是动力不足时的糙米。
 *
 * <p>版式刻意把**两级产物**并排摆出来——因为「不及則粗」是这本书原话里最有味道的一条机制，
 * 玩家在 JEI 里一眼就该看懂"动力够不够，出的东西不一样"。
 */
public class PoundingRecipeCategory implements IRecipeCategory<PoundingRecipe> {

    private static final int WIDTH = 124;
    private static final int HEIGHT = 68;

    /** 输入格。 */
    private static final int IN_X = 8;
    private static final int ROW_MAIN = 22;
    private static final int ARROW_X = 30;
    /** 舂透时：白米。 */
    private static final int MAIN_X = 54;
    /** 副产物：细糠。 */
    private static final int SIDE_X = 78;
    /** 动力不足时：糙米。 */
    private static final int ROW_COARSE = 48;
    /** 说明文字的行位（贴着两行格子）。 */
    private static final int TEXT_MAIN_Y = 4;
    private static final int TEXT_COARSE_Y = 52;

    private final IDrawable icon;

    public PoundingRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(TiangongKaiwu.DUI_ITEM.get());
    }

    @Override
    public RecipeType<PoundingRecipe> getRecipeType() {
        return TiangongKaiwuJeiPlugin.POUNDING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.tiangongkaiwu.category.pounding");
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, PoundingRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(IN_X, ROW_MAIN)
                .addIngredients(recipe.input())
                .addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(Component.translatable("jei.tiangongkaiwu.pounding.input")));

        builder.addOutputSlot(MAIN_X, ROW_MAIN)
                .addItemStack(recipe.result())
                .addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(Component.translatable("jei.tiangongkaiwu.pounding.result", recipe.minPower())));

        if (!recipe.byproduct().isEmpty()) {
            builder.addOutputSlot(SIDE_X, ROW_MAIN)
                    .addItemStack(recipe.byproduct())
                    .addRichTooltipCallback((view, tooltip) ->
                            tooltip.add(Component.translatable("jei.tiangongkaiwu.pounding.byproduct")));
        }

        if (!recipe.coarse().isEmpty()) {
            builder.addOutputSlot(MAIN_X, ROW_COARSE)
                    .addItemStack(recipe.coarse())
                    .addRichTooltipCallback((view, tooltip) ->
                            tooltip.add(Component.translatable("jei.tiangongkaiwu.pounding.coarse", recipe.minPower())));
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, PoundingRecipe recipe, IFocusGroup focuses) {
        builder.addRecipeArrowWidget().setPosition(ARROW_X, ROW_MAIN + 4);
        builder.addText(Component.translatable("jei.tiangongkaiwu.pounding.power", recipe.minPower()),
                2, TEXT_MAIN_Y);
        if (!recipe.coarse().isEmpty()) {
            builder.addText(Component.translatable("jei.tiangongkaiwu.pounding.coarse_label"),
                    2, TEXT_COARSE_Y);
        }
    }
}
