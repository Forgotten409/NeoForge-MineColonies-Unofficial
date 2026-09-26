package com.ldtteam.domumornamentum.jei;

import com.ldtteam.domumornamentum.IDomumOrnamentumApi;
import com.ldtteam.domumornamentum.block.IModBlocks;
import com.ldtteam.domumornamentum.client.screens.ArchitectsCutterScreen;
import com.ldtteam.domumornamentum.util.Constants;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * JEI integration — PORT 26.1.2 (JEI 29.x).
 *
 * <p>Structure identical to the 1.21.1 plugin; API adaptations:
 * <ul>
 *   <li>{@code ResourceLocation} → {@link Identifier} (vanilla 26.1 rename);</li>
 *   <li>subtype registration now takes the renamed {@link ISubtypeInterpreter}
 *       (see {@link MaterialSubtypeInterpreter});</li>
 *   <li>recipes come from {@link ClientCutterRecipeCache} — NeoForge's recipe sync
 *       (see {@link CutterRecipeServerSyncHandler}), replacing the removed client
 *       {@code RecipeManager#getAllRecipesFor};</li>
 *   <li>catalyst registration uses {@code addCraftingStation} (the old
 *       {@code addRecipeCatalyst} is deprecated-for-removal in JEI 29.x).</li>
 * </ul>
 */
@JeiPlugin
public class JEIPlugin implements IModPlugin
{
    private IIngredientManager ingredientManager;

    @Nullable
    public IIngredientManager getIngredientManager()
    {
        return this.ingredientManager;
    }

    @Override
    public Identifier getPluginUid()
    {
        return Constants.resLocDO(Constants.MOD_ID);
    }

    @Override
    public void registerItemSubtypes(final ISubtypeRegistration registration)
    {
        final IModBlocks blocks = IDomumOrnamentumApi.getInstance().getBlocks();
        final MaterialSubtypeInterpreter interpreter = MaterialSubtypeInterpreter.getInstance();

        registration.registerSubtypeInterpreter(blocks.getDoor().asItem(), interpreter);
        registration.registerSubtypeInterpreter(blocks.getTrapdoor().asItem(), interpreter);
        registration.registerSubtypeInterpreter(blocks.getFancyDoor().asItem(), interpreter);
        registration.registerSubtypeInterpreter(blocks.getFancyTrapdoor().asItem(), interpreter);
        registration.registerSubtypeInterpreter(blocks.getPost().asItem(), interpreter);
        registration.registerSubtypeInterpreter(blocks.getPanel().asItem(), interpreter);
    }

    @Override
    public void registerCategories(final IRecipeCategoryRegistration registration)
    {
        final ArchitectsCutterCategory category =
          new ArchitectsCutterCategory(registration.getJeiHelpers().getGuiHelper(), this);
        registration.addRecipeCategories(category);
    }

    @Override
    public void registerRecipes(final IRecipeRegistration registration)
    {
        registration.addRecipes(ArchitectsCutterCategory.TYPE, ClientCutterRecipeCache.getRecipes());
    }

    @Override
    public void registerRecipeCatalysts(final IRecipeCatalystRegistration registration)
    {
        registration.addCraftingStation(ArchitectsCutterCategory.TYPE,
          new ItemStack(IDomumOrnamentumApi.getInstance().getBlocks().getArchitectsCutter()));
    }

    @Override
    public void registerGuiHandlers(final IGuiHandlerRegistration registration)
    {
        registration.addGhostIngredientHandler(ArchitectsCutterScreen.class, new ArchitectsCutterGuiHandler());
    }

    @Override
    public void onRuntimeAvailable(final IJeiRuntime jeiRuntime)
    {
        this.ingredientManager = jeiRuntime.getIngredientManager();
    }
}
