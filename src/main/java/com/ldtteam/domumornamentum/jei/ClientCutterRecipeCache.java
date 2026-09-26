package com.ldtteam.domumornamentum.jei;

import com.ldtteam.domumornamentum.recipe.ModRecipeTypes;
import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;

import java.util.List;

/**
 * Client-side cache of the Architect's Cutter recipes synced by the server — PORT 26.1.2.
 *
 * <p>PORT26: in 1.21.1 the client had the full {@code RecipeManager} (vanilla synced every recipe),
 * so the JEI plugin could call {@code Minecraft.getInstance().level.getRecipeManager()
 * .getAllRecipesFor(...)} directly. Since 1.21.2 vanilla only syncs recipe <em>displays</em>
 * ({@code RecipePropertySet}s) to the client — the full {@code Recipe} objects (which the cutter's
 * dynamic "material in → variant out" assembly needs) are no longer available that way.
 *
 * <p>NeoForge's replacement protocol (26.1):
 * <ul>
 *   <li>server: mods request recipe types to sync in {@link CutterRecipeServerSyncHandler}
 *       via {@code OnDatapackSyncEvent#sendRecipes};</li>
 *   <li>client: {@link CutterRecipeClientSyncHandler} receives {@code RecipesReceivedEvent}
 *       and stores the synced {@link RecipeMap} here.</li>
 * </ul>
 *
 * <p>The cache is deliberately free of any JEI import: it works with or without JEI installed
 * and never forces the {@code mezz.jei} classes to classload (those are only reachable through
 * the JEI plugin, which JEI itself discovers via the {@code @JeiPlugin} annotation).
 */
public final class ClientCutterRecipeCache
{
    private static volatile List<RecipeHolder<ArchitectsCutterRecipe>> recipes = List.of();

    private ClientCutterRecipeCache()
    {
    }

    /**
     * Replaces the cache with the cutter recipes contained in a synced recipe map
     * (called from {@code RecipesReceivedEvent}).
     */
    public static void update(final RecipeMap recipeMap)
    {
        recipes = List.copyOf(recipeMap.byType(ModRecipeTypes.ARCHITECTS_CUTTER.get()));
    }

    /**
     * Drops the cached recipes (called on disconnect — the recipes belong to the previous
     * connection's server).
     */
    public static void clear()
    {
        recipes = List.of();
    }

    /**
     * The synced cutter recipes; empty before the first sync or on connections that did not
     * send them (vanilla servers).
     */
    public static List<RecipeHolder<ArchitectsCutterRecipe>> getRecipes()
    {
        return recipes;
    }
}
