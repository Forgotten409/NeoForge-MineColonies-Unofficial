package com.ldtteam.blockui.mod;

import com.ldtteam.blockui.AtlasManager;
import com.ldtteam.blockui.Loader;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.event.ModMismatchEvent;

public class ClientLifecycleSubscriber
{
    @SubscribeEvent
    public static void onAddClientReloadListeners(final AddClientReloadListenersEvent event)
    {
        // PORT26: RegisterClientReloadListenersEvent#registerReloadListener(PreparableReloadListener)
        // became AddClientReloadListenersEvent#addListener(Identifier, PreparableReloadListener).
        event.addListener(Identifier.fromNamespaceAndPath(BlockUI.MOD_ID, "blockui_loader"), Loader.INSTANCE);

        // custom per-mod GUI atlases are registered through RegisterTextureAtlasesEvent now
        // (see AtlasManager.onRegisterTextureAtlases); mods announce their atlas via
        // AtlasManager.registerModAtlas(...) from their constructors.
    }

    /**
     * PORT26: the old {@code RegisterColorHandlersEvent.Block} handler that re-tinted the
     * water cauldron (test GUI only) was dropped — the event variant no longer exists in
     * 26.1.2 (block tinting moved to data-driven tint sources).
     */

    @SubscribeEvent
    public static void onModMismatch(final ModMismatchEvent event)
    {
        // there are no world data and rest is mod compat anyway
        event.getVersionDifference(BlockUI.MOD_ID).ifPresent(id -> event.markResolved(BlockUI.MOD_ID));
    }
}
