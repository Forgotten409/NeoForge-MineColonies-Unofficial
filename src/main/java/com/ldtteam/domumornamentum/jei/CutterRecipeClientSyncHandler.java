package com.ldtteam.domumornamentum.jei;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

/**
 * Client half of the cutter recipe sync — PORT 26.1.2.
 *
 * <p>Listens for NeoForge's {@code RecipesReceivedEvent} (fired after the server accepted our
 * request from {@link CutterRecipeServerSyncHandler}) and hands the synced recipes to
 * {@link ClientCutterRecipeCache}. The cache is cleared when the player logs out, as advised
 * by the event's javadoc.
 *
 * <p>Registered as an instance on the game bus from the client branch of the mod bootstrap
 * (see {@code DomumOrnamentum#init}); contains client-only event classes and must never be
 * registered on a dedicated server.
 */
public class CutterRecipeClientSyncHandler
{
    private static final CutterRecipeClientSyncHandler INSTANCE = new CutterRecipeClientSyncHandler();

    public static CutterRecipeClientSyncHandler getInstance()
    {
        return INSTANCE;
    }

    private CutterRecipeClientSyncHandler()
    {
    }

    @SubscribeEvent
    public void onRecipesReceived(final RecipesReceivedEvent event)
    {
        ClientCutterRecipeCache.update(event.getRecipeMap());
    }

    @SubscribeEvent
    public void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event)
    {
        ClientCutterRecipeCache.clear();
    }
}
