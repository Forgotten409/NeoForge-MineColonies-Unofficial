package com.minecolonies.api.crafting;

import com.google.common.collect.Lists;
import com.minecolonies.api.crafting.registry.ModRecipeSerializer;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.*;
import net.minecraft.advancements.criterion.RecipeUnlockedTrigger;
import net.minecraft.core.NonNullList;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A shapeless recipe that discards any remaining items.  Mainly intended for mixing things into bottles or bowls
 * without leaving extra empties behind, but can be used for other things too.
 */
public class ZeroWasteRecipe extends ShapelessRecipe
{
    /**
     * PORT26: ShapelessRecipe ctor is (CommonInfo, CraftingBookInfo, ItemStackTemplate, List<Ingredient>) now,
     * and the result/ingredients fields are private — keep local copies for codec/network access.
     */
    private final ItemStackTemplate zwResult;
    private final List<Ingredient> zwIngredients;

    public ZeroWasteRecipe(@NotNull final ItemStack output,
                           @NotNull final NonNullList<Ingredient> inputs)
    {
        this(new Recipe.CommonInfo(true),
          new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
          ItemStackTemplate.fromNonEmptyStack(output),
          inputs);
    }

    public ZeroWasteRecipe(@NotNull final Recipe.CommonInfo commonInfo,
                           @NotNull final CraftingRecipe.CraftingBookInfo bookInfo,
                           @NotNull final ItemStackTemplate result,
                           @NotNull final List<Ingredient> inputs)
    {
        super(commonInfo, bookInfo, result, inputs);
        this.zwResult = result;
        this.zwIngredients = inputs;
    }

    @NotNull
    @Override
    public NonNullList<ItemStack> getRemainingItems(@NotNull final CraftingInput input)
    {
        final NonNullList<ItemStack> remainingItems = super.getRemainingItems(input);
        Collections.fill(remainingItems, ItemStack.EMPTY);
        return remainingItems;
    }

    @NotNull
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public RecipeSerializer<ShapelessRecipe> getSerializer()
    {
        // PORT26: ShapelessRecipe#getSerializer returns the invariant RecipeSerializer<ShapelessRecipe>
        // (like vanilla FireworkRocketRecipe vs CustomRecipe's wildcard) — the registered serializer
        // is RecipeSerializer<ZeroWasteRecipe>, erased at this point, so an unchecked cast is required.
        return (RecipeSerializer<ShapelessRecipe>)(RecipeSerializer<?>) ModRecipeSerializer.ZeroWasteRecipeSerializer.get();
    }

    private static final MapCodec<ZeroWasteRecipe> CODEC = RecordCodecBuilder.mapCodec(
            builder -> builder.group(
                    Recipe.CommonInfo.MAP_CODEC.forGetter(r -> r.commonInfo),
                    CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(r -> r.bookInfo),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.zwResult),
                    Ingredient.CODEC.listOf(1, 9).fieldOf("ingredients").forGetter(r -> r.zwIngredients)
            ).apply(builder, ZeroWasteRecipe::new)
    );
    private static final StreamCodec<RegistryFriendlyByteBuf, ZeroWasteRecipe> STREAM_CODEC = StreamCodec.of(
            ZeroWasteRecipe::toNetwork, ZeroWasteRecipe::fromNetwork
    );

    /**
     * PORT26: RecipeSerializer is a record now.
     */
    public static final RecipeSerializer<ZeroWasteRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    private static ZeroWasteRecipe fromNetwork(@NotNull final RegistryFriendlyByteBuf buf)
    {
        final Recipe.CommonInfo commonInfo = Recipe.CommonInfo.STREAM_CODEC.decode(buf);
        final CraftingRecipe.CraftingBookInfo bookInfo = CraftingRecipe.CraftingBookInfo.STREAM_CODEC.decode(buf);
        final ItemStackTemplate result = ItemStackTemplate.STREAM_CODEC.decode(buf);
        final int count = buf.readVarInt();
        final List<Ingredient> inputs = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            inputs.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
        }

        return new ZeroWasteRecipe(commonInfo, bookInfo, result, inputs);
    }

    private static void toNetwork(@NotNull final RegistryFriendlyByteBuf buf,
                                  @NotNull final ZeroWasteRecipe recipe)
    {
        Recipe.CommonInfo.STREAM_CODEC.encode(buf, recipe.commonInfo);
        CraftingRecipe.CraftingBookInfo.STREAM_CODEC.encode(buf, recipe.bookInfo);
        ItemStackTemplate.STREAM_CODEC.encode(buf, recipe.zwResult);
        buf.writeVarInt(recipe.zwIngredients.size());
        for (final Ingredient input : recipe.zwIngredients)
        {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, input);
        }
    }

    public static Builder build(@NotNull final RecipeCategory category,
                                @NotNull final ItemLike output,
                                final int count)
    {
        return new Builder(category, new ItemStack(output, count));
    }

    public static Builder build(@NotNull final RecipeCategory category,
                                @NotNull final ItemStack output)
    {
        return new Builder(category, output);
    }

    public static class Builder implements RecipeBuilder
    {
        private final RecipeCategory category;
        private final ItemStack output;
        private final List<Ingredient> ingredients = Lists.newArrayList();
        private final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();

        public Builder(@NotNull final RecipeCategory category,
                       @NotNull final ItemStack output)
        {
            this.category = category;
            this.output = output;
        }

        public Builder requires(@NotNull final ItemLike item)
        {
            return this.requires(item, 1);
        }

        public Builder requires(@NotNull final ItemLike item, final int count)
        {
            for (int i = 0; i < count; ++i)
            {
                this.requires(Ingredient.of(item));
            }
            return this;
        }

        public Builder requires(@NotNull final Ingredient ingredient)
        {
            return this.requires(ingredient, 1);
        }

        public Builder requires(@NotNull final Ingredient ingredient, final int count)
        {
            for (int i = 0; i < count; ++i)
            {
                this.ingredients.add(ingredient);
            }
            return this;
        }

        @NotNull
        public Builder unlockedBy(@NotNull final String name, @NotNull final Criterion<?> criterion)
        {
            this.criteria.put(name, criterion);
            return this;
        }

        @Override
        public net.minecraft.resources.ResourceKey<Recipe<?>> defaultId()
        {
            return RecipeBuilder.getDefaultRecipeId(this.output);
        }

        @NotNull
        public Item getResult()
        {
            return this.output.getItem();
        }

        @NotNull
        @Override
        public RecipeBuilder group(@Nullable String group)
        {
            return this;
        }

        @Override
        public void save(@NotNull final RecipeOutput consumer, @NotNull final net.minecraft.resources.ResourceKey<Recipe<?>> id)
        {
            this.ensureValid(id);

            final ZeroWasteRecipe recipe = new ZeroWasteRecipe(this.output, NonNullList.copyOf(this.ingredients));

            final Advancement.Builder advancementBuilder = consumer.advancement();
            advancementBuilder
                    .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(id))
                    .rewards(AdvancementRewards.Builder.recipe(id))
                    .requirements(AdvancementRequirements.Strategy.OR);
            this.criteria.forEach(advancementBuilder::addCriterion);
            final AdvancementHolder advancement = advancementBuilder.build(id.identifier().withPrefix("recipes/" + this.category.getFolderName() + "/"));

            consumer.accept(id, recipe, advancement);
        }

        /**
         * PORT26 compat overload: recipe ids are ResourceKeys now; datagen callers pass Identifiers.
         */
        public void save(@NotNull final RecipeOutput consumer, @NotNull final Identifier id)
        {
            this.save(consumer, net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id));
        }

        private void ensureValid(@NotNull final net.minecraft.resources.ResourceKey<Recipe<?>> id)
        {
            if (this.criteria.isEmpty())
            {
                throw new IllegalStateException("No way of obtaining recipe " + id);
            }
        }
    }
}
