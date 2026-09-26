package com.minecolonies.core.recipes;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.recipe.ModRecipeTypes;
import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipe;
import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipeInput;
import com.minecolonies.api.crafting.GenericRecipe;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.crafting.ModCraftingTypes;
import com.minecolonies.api.crafting.RecipeCraftingType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class ArchitectsCutterCraftingType extends RecipeCraftingType<ArchitectsCutterRecipeInput, ArchitectsCutterRecipe>
{
    public ArchitectsCutterCraftingType()
    {
        super(ModCraftingTypes.ARCHITECTS_CUTTER_ID, ModRecipeTypes.ARCHITECTS_CUTTER.get(), null);
    }

    @Override
    public @NotNull List<IGenericRecipe> findRecipes(@NotNull RecipeManager recipeManager, @Nullable Level world)
    {
        final Random rnd = new Random();
        final List<IGenericRecipe> recipes = new ArrayList<>();
        // PORT26: RecipeManager#getAllRecipesFor is gone — filter the full collection by type.
        for (final RecipeHolder<?> untyped : recipeManager.getRecipes())
        {
        if (!(untyped.value() instanceof final ArchitectsCutterRecipe recipeValue)) continue;
        final RecipeHolder<ArchitectsCutterRecipe> holder = new RecipeHolder<>(untyped.id(), recipeValue);
        {
            final ArchitectsCutterRecipe recipe = holder.value();
            // cutter recipes don't implement getIngredients(), so we have to work around it
            final Block generatedBlock = BuiltInRegistries.BLOCK.getValue(recipe.getBlockName());

            if (!(generatedBlock instanceof final IMateriallyTexturedBlock materiallyTexturedBlock))
                continue;

            final List<List<ItemStack>> inputs = new ArrayList<>();
            for (final IMateriallyTexturedBlockComponent component : materiallyTexturedBlock.getComponents())
            {
                // PORT26: Registry#getTag(TagKey) is gone — getTagOrEmpty returns the
                // (possibly empty) iterable of holders directly.
                final List<Block> blocks = new ArrayList<>();
                // PORT26: renamed the loop variable — 'holder' collides with the RecipeHolder above.
                for (final Holder<Block> skin : BuiltInRegistries.BLOCK.getTagOrEmpty(component.getValidSkins()))
                {
                    blocks.add(skin.value());
                }
                if (!blocks.isEmpty())
                {
                    Collections.shuffle(blocks, rnd);
                    inputs.add(blocks.stream().map(ItemStack::new).collect(Collectors.toList()));
                }
            }

            final ItemStack output = recipe.getResultItem().copy();
            output.setCount(Math.max(recipe.getCount(), inputs.size()));
            MaterialTextureData.EMPTY.writeToItemStack(output);

            recipes.add(GenericRecipe.builder()
                    .withRecipeId(holder.id().identifier())
                    .withOutput(output)
                    .withInputs(inputs)
                    .withGridSize(3)
                    .build());
        }

        }

        return recipes;
    }
}
