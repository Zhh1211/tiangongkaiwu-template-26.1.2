package com.example.tiangongkaiwu.recipe;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * 舂米（碓）配方。数据驱动，放在 {@code data/<ns>/recipe/} 下，type 写
 * {@code tiangongkaiwu:pounding}。
 *
 * <p>依据《天工開物·粹精·攻稻》：「凡稻去殼用礱，去膜用舂……**不及則粗，太過則粉**，
 * 精糧從此出焉。既舂以後，皮膜成粉，名曰**細糠**，以供犬豕之豢。」
 *
 * <p>所以一条配方有四件东西：
 * <ul>
 *   <li>{@code input} —— 投进碓的料（谷／糙米……）</li>
 *   <li>{@code min_power} —— **舂得透**所需的动力（轴上 power）。不足就是书里那句「不及則粗」</li>
 *   <li>{@code result} + {@code byproduct} —— 舂透时出：米 + 细糠</li>
 *   <li>{@code coarse} —— 舂不透时出：糙米（可再投入碓重舂；留空表示动力不足就干等，不糟蹋料）</li>
 * </ul>
 *
 * <p>示例 JSON：
 * <pre>
 * {
 *   "type": "tiangongkaiwu:pounding",
 *   "input": { "item": "tiangongkaiwu:rice_grain" },
 *   "result": { "id": "tiangongkaiwu:rice", "count": 1 },
 *   "coarse": { "id": "tiangongkaiwu:brown_rice", "count": 1 },
 *   "byproduct": { "id": "tiangongkaiwu:rice_bran", "count": 1 },
 *   "min_power": 3
 * }
 * </pre>
 */
public class PoundingRecipe implements Recipe<SingleRecipeInput> {

    private final Ingredient input;
    private final ItemStack result;
    private final ItemStack coarse;
    private final ItemStack byproduct;
    private final int minPower;

    public PoundingRecipe(Ingredient input, ItemStack result, ItemStack coarse,
                          ItemStack byproduct, int minPower) {
        this.input = input;
        this.result = result;
        this.coarse = coarse;
        this.byproduct = byproduct;
        this.minPower = minPower;
    }

    public Ingredient input() {
        return this.input;
    }

    public ItemStack result() {
        return this.result;
    }

    /** 动力不足时的产物（可为空 = 不产，干等）。 */
    public ItemStack coarse() {
        return this.coarse;
    }

    /** 副产物（细糠）。 */
    public ItemStack byproduct() {
        return this.byproduct;
    }

    /** 舂透所需的轴上动力。 */
    public int minPower() {
        return this.minPower;
    }

    public static final MapCodec<PoundingRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("input").forGetter(PoundingRecipe::input),
            ItemStack.CODEC.fieldOf("result").forGetter(PoundingRecipe::result),
            ItemStack.CODEC.optionalFieldOf("coarse", ItemStack.EMPTY).forGetter(PoundingRecipe::coarse),
            ItemStack.CODEC.optionalFieldOf("byproduct", ItemStack.EMPTY).forGetter(PoundingRecipe::byproduct),
            Codec.INT.optionalFieldOf("min_power", 3).forGetter(PoundingRecipe::minPower)
    ).apply(inst, PoundingRecipe::new));

    // ⚠️ coarse / byproduct 允许为空（如「糙米→米」这条配方没有粗制品），
    // 必须用 OPTIONAL_STREAM_CODEC —— 普通的 ItemStack.STREAM_CODEC 编码空 stack 会抛
    // 「Empty ItemStack not allowed」，导致 update_recipes 包发不出去、客户端直接掉线。
    public static final StreamCodec<RegistryFriendlyByteBuf, PoundingRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, recipe) -> {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.input);
                ItemStack.STREAM_CODEC.encode(buf, recipe.result);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, recipe.coarse);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, recipe.byproduct);
                buf.writeVarInt(recipe.minPower);
            },
            buf -> new PoundingRecipe(
                    Ingredient.CONTENTS_STREAM_CODEC.decode(buf),
                    ItemStack.STREAM_CODEC.decode(buf),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    buf.readVarInt()));

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return this.input.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return this.result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, this.input);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return TiangongKaiwu.POUNDING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return TiangongKaiwu.POUNDING_TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<PoundingRecipe> {

        @Override
        public MapCodec<PoundingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PoundingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
