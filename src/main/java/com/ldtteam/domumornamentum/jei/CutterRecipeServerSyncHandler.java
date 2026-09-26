package com.ldtteam.domumornamentum.jei;

import com.ldtteam.domumornamentum.recipe.ModRecipeTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * Server half of the cutter recipe sync — PORT 26.1.2.
 *
 * <p>Requests NeoForge to sync the Architect's Cutter recipe type to joining players (and on
 * datapack reloads). Without this call the client never receives the full {@code ArchitectsCutterRecipe}
 * objects — since 1.21.2 vanilla only syncs recipe displays, which cannot express the cutter's
 * dynamic "materials in → textured variant out" behaviour for recipe viewers like JEI.
 *
 * <p>Registered as an instance on the game bus from the mod bootstrap
 * (see {@code DomumOrnamentum#init}); safe on both sides (the event only fires on the server,
 * and the class only references common code).
 */
public class CutterRecipeServerSyncHandler
{
    private static final CutterRecipeServerSyncHandler INSTANCE = new CutterRecipeServerSyncHandler();

    public static CutterRecipeServerSyncHandler getInstance()
    {
        return INSTANCE;
    }

    private CutterRecipeServerSyncHandler()
    {
    }

    @SubscribeEvent
    public void onDatapackSync(final OnDatapackSyncEvent event)
    {
        event.sendRecipes(ModRecipeTypes.ARCHITECTS_CUTTER.get());
    }
}
