package com.minecolonies.api.crafting;

import com.minecolonies.api.crafting.registry.CraftingType;
import com.minecolonies.api.util.Log;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A {@link CraftingType} for the vanilla {@link RecipeType}
 * @param <C> the crafting inventory type
 * @param <T> the recipe type
 */
public class RecipeCraftingType<C extends RecipeInput, T extends Recipe<C>> extends CraftingType
{
    private final RecipeType<T> recipeType;
    private final Predicate<RecipeHolder<T>> predicate;

    /**
     * Create a new instance
     * @param id the crafting type id
     * @param recipeType the vanilla recipe type
     * @param predicate filter acceptable recipes, or null to accept all
     */
    public RecipeCraftingType(@NotNull final Identifier id,
                              @NotNull final RecipeType<T> recipeType,
                              @Nullable final Predicate<RecipeHolder<T>> predicate)
    {
        super(id);
        this.recipeType = recipeType;
        this.predicate = predicate;
    }

    @Override
    @NotNull
    @SuppressWarnings("unchecked")   // PORT26: getAllRecipesFor is gone; filter the full collection
    public List<IGenericRecipe> findRecipes(@NotNull RecipeManager recipeManager,
                                            @NotNull final Level world)
    {
        final List<IGenericRecipe> recipes = new ArrayList<>();
        for (final RecipeHolder<?> holder : recipeManager.getRecipes())
        {
            if (holder.value().getType() != recipeType) continue;
            final RecipeHolder<T> recipe = (RecipeHolder<T>) holder;
            if (predicate != null && !predicate.test(recipe)) continue;

            tryAddingVanillaRecipe(recipes, recipe, world);
        }
        return recipes;
    }

    private void tryAddingVanillaRecipe(@NotNull final List<IGenericRecipe> recipes,
                                               @NotNull final RecipeHolder<T> holder,
                                               @NotNull final Level world)
    {
        final T recipe = holder.value();
        if (recipe.isSpecial() || getResultItem(recipe).isEmpty()) return;     // invalid or special recipes
        try
        {
            final IGenericRecipe genericRecipe = GenericRecipe.of(holder, world);
            if (genericRecipe == null || genericRecipe.getInputs().isEmpty()) return;
            recipes.add(genericRecipe);
        }
        catch (final Exception ex)
        {
            Log.getLogger().warn("Error evaluating recipe " + holder.id() + "; ignoring.", ex);
        }
    }

    /**
     * PORT26: Recipe#getResultItem(registryAccess) is gone; the result item is produced
     * by {@code assemble(input)} which (for crafting and single-item recipes) does not
     * actually read the input when only the raw result is needed.
     *
     * <p>PORT26 FIX (JEI recipe-load crash): that assumption does not hold for all 26.x
     * vanilla recipe types — e.g. {@code ImbueRecipe.assemble} indexes the crafting input
     * ({@code input.getItem(1)}), which throws {@code ArrayIndexOutOfBoundsException} on
     * the 0-slot {@link CraftingInput#EMPTY}. The exception escaped through
     * RecipeCraftingType.findRecipes into JEIPlugin.onRecipesLoaded and aborted the whole
     * CustomRecipesReloadedEvent handler — minecolonies recipes then never registered in
     * JEI. Guard the probe and report no static result for such recipes instead.</p>
     *
     * @param recipe the recipe.
     * @return the result stack, or {@link net.minecraft.world.item.ItemStack#EMPTY}.
     */
    @NotNull
    public static ItemStack getResultItem(@NotNull final Recipe<?> recipe)
    {
        try
        {
            if (recipe instanceof final CraftingRecipe craftingRecipe)
            {
                return craftingRecipe.assemble(CraftingInput.EMPTY);
            }
            if (recipe instanceof final SingleItemRecipe singleItemRecipe)
            {
                return singleItemRecipe.assemble(new SingleRecipeInput(net.minecraft.world.item.ItemStack.EMPTY));
            }
        }
        catch (final IndexOutOfBoundsException | ArithmeticException | IllegalStateException ex)
        {
            // Recipe types that actually need a concrete input (ImbueRecipe etc.) have no
            // static result — treat as empty so the caller skips them for JEI listing.
            Log.getLogger().debug("Recipe {} has no static result item (needs concrete input): {}", recipe.getClass().getSimpleName(), ex.toString());
        }
        return ItemStack.EMPTY;
    }
}
