package com.ldtteam.structurize.event;

import com.ldtteam.common.language.LanguageHandler;
import com.ldtteam.minecolonies.MineColonies;
import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.network.messages.*;
import com.ldtteam.structurize.storage.ServerStructurePackLoader;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.NotNull;

public class LifecycleSubscriber
{
    @SubscribeEvent
    public static void onNetworkRegistry(final RegisterPayloadHandlersEvent event)
    {
        // PORT26 (merged single-mod): the only mod container is "minecolonies" — a lookup
        // by "structurize" returns an empty Optional (same as MultiPiston.NetworkHandler).
        // Also, registrar(String) now takes the network VERSION directly (the old
        // namespace-scoped registrar + .versioned() chain was removed in 26.1).
        final String modVersion = ModList.get()
            .getModContainerById(MineColonies.MOD_ID)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse("1");

        final PayloadRegistrar registry = event.registrar(modVersion);

        AbsorbBlockMessage.TYPE.register(registry);
        AddRemoveTagMessage.TYPE.register(registry);
        BlueprintSyncMessage.TYPE.register(registry);
        BuildToolPlacementMessage.TYPE.register(registry);
        ClientBlueprintRequestMessage.TYPE.register(registry);
        FillTopPlaceholderMessage.TYPE.register(registry);
        ItemMiddleMouseMessage.TYPE.register(registry);
        NotifyClientAboutStructurePacksMessage.TYPE.register(registry);
        NotifyServerAboutStructurePacksMessage.TYPE.register(registry);
        OperationHistoryMessage.TYPE.register(registry);
        RemoveBlockMessage.TYPE.register(registry);
        RemoveEntityMessage.TYPE.register(registry);
        ReplaceBlockMessage.TYPE.register(registry);
        SaveScanMessage.TYPE.register(registry);
        ScanOnServerMessage.TYPE.register(registry);
        ScanToolTeleportMessage.TYPE.register(registry);
        SetTagInTool.TYPE.register(registry);
        ShowScanMessage.TYPE.register(registry);
        SyncPosSelectionMessage.TYPE.register(registry);
        SyncPreviewCacheToClient.TYPE.register(registry);
        SyncPreviewCacheToServer.TYPE.register(registry);
        SyncSettingsToServer.TYPE.register(registry);
        TransferStructurePackToClient.TYPE.register(registry);
        UndoRedoMessage.TYPE.register(registry);
        UpdateClientRender.TYPE.register(registry);
        UpdateScanToolMessage.TYPE.register(registry);
    }

    /**
     * Called when MC loading is about to finish.
     *
     * @param event event
     */
    @SubscribeEvent
    public static void onLoadComplete(final FMLLoadCompleteEvent event)
    {
        LanguageHandler.setMClanguageLoaded();
    }

    @SubscribeEvent
    public static void onDedicatedServerInit(final FMLDedicatedServerSetupEvent event)
    {
        ServerStructurePackLoader.onServerStarting();
    }

    // PORT26: the 1.21.1 onDatagen (GatherDataEvent) handler was dropped — the datagen
    // package is intentionally not ported (dev-time only, see STRUCTURIZE_PORT_GUIDE.md).
}
