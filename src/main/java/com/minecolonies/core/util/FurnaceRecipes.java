package com.minecolonies.core.util;

import com.google.common.collect.ImmutableList;
import com.minecolonies.api.compatibility.IFurnaceRecipes;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.RecipeStorage;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class FurnaceRecipes implements IFurnaceRecipes
{
    /**
     * Furnace recipes.
     */
    private Map<ItemStorage, RecipeStorage> recipes = new HashMap<>();
    private Map<ItemStorage, RecipeStorage> reverseRecipes = new HashMap<>();

    /**
     * PORT26: client-side overload — the client receives a {@link net.minecraft.world.item.crafting.RecipeMap} (via
     * RecipesReceivedEvent) instead of the server's {@link RecipeManager}.
     *
     * @param recipeMap the synced recipe map to parse.
     * @param level     the client level.
     */
    public void loadRecipes(final net.minecraft.world.item.crafting.RecipeMap recipeMap, final Level level)
    {
        recipes.clear();
        reverseRecipes.clear();
        recipeMap.byType(RecipeType.SMELTING).forEach(holder ->
            parseSmeltingRecipe(holder, level));
    }

    public void loadRecipes(final RecipeManager recipeManager, final Level level)
    {
        recipes.clear();
        reverseRecipes.clear();
        // PORT26: RecipeManager#getAllRecipesFor is gone — filter the full collection by
        // recipe type instead (equivalent to the old typed lookup).
        recipeManager.getRecipes().stream()
          .filter(holder -> holder.value() instanceof SmeltingRecipe)
          .forEach(holder -> parseSmeltingRecipe(holder, level));
    }

    /**
     * Shared smelting-recipe parser (input/output pair extraction) used by both the
     * server ({@link RecipeManager}) and client ({@link net.minecraft.world.item.crafting.RecipeMap}) loaders.
     *
     * <p>PORT26: the recipe API changed — cooking recipes expose their single input via
     * {@code input()} (Ingredient over item holders instead of getItems() stacks) and
     * the result via {@code assemble(SingleRecipeInput)} (the old
     * {@code getResultItem(registryAccess)} is gone).</p>
     */
    private void parseSmeltingRecipe(final net.minecraft.world.item.crafting.RecipeHolder<? extends net.minecraft.world.item.crafting.Recipe<?>> holder, final Level level)
    {
        final SmeltingRecipe recipe = (SmeltingRecipe) holder.value();
        for (final net.minecraft.core.Holder<net.minecraft.world.item.Item> smeltableItem : recipe.input().items().toList())
        {
            final ItemStack smeltable = new ItemStack(smeltableItem.value());
            if (!smeltable.isEmpty())
            {
                final ItemStack result = recipe.assemble(new net.minecraft.world.item.crafting.SingleRecipeInput(ItemStack.EMPTY));
                final RecipeStorage storage = RecipeStorage.builder()
                        .withInputs(ImmutableList.of(new ItemStorage(smeltable)))
                        .withPrimaryOutput(result)
                        .withGridSize(1)
                        .withIntermediate(Blocks.FURNACE)
                        .withRecipeId(holder.id().identifier())
                        .build();

                recipes.put(storage.getCleanedInput().get(0), storage);

                final ItemStack output = result.copy();
                output.setCount(1);
                reverseRecipes.put(new ItemStorage(output), storage);
            }
        }
    }

    @Override
    public ItemStack getSmeltingResult(final ItemStack itemStack)
    {
        final RecipeStorage storage = recipes.getOrDefault(new ItemStorage(itemStack), null);
        if (storage != null)
        {
            return storage.getPrimaryOutput();
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    @Override
    public RecipeStorage getFirstSmeltingRecipeByResult(final ItemStorage storage)
    {
        return reverseRecipes.get(storage);
    }
}
