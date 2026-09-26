package com.ldtteam.domumornamentum.event.handlers;

import com.ldtteam.domumornamentum.network.messages.CreativeSetArchitectCutterSlotMessage;
import com.ldtteam.minecolonies.MineColonies;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Common mod-bus event handlers — PORT 26.1.2.
 *
 * <p>Registered manually from {@code DomumOrnamentum.init} (merged single-mod port).
 *
 * <p>PORT26: the 1.21.1 {@code GatherDataEvent} datagen wiring lived here — the 87 datagen
 * provider classes are being ported to the new 26.1.2 vanilla datagen system
 * ({@code net.minecraft.client.data.models.ModelProvider}) in a follow-up pass; see PORTING.md.
 */
public class ModBusEventHandler
{
    /**
     * Registers the payload handlers of the mod.
     *
     * @param event event
     */
    @SubscribeEvent
    public static void onNetworkRegistry(final RegisterPayloadHandlersEvent event)
    {
        // PORT26 (merged single-mod): the only mod container is "minecolonies" — a lookup by
        // "domum_ornamentum" returns an empty Optional and crashed here with
        // NoSuchElementException during RegisterPayloadHandlersEvent dispatch.
        final String modVersion = ModList.get()
            .getModContainerById(MineColonies.MOD_ID)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse("1");

        // PORT26: registrar(String) now takes the network VERSION directly (the old
        // namespace-scoped registrar was removed) — no separate .versioned() call needed.
        // NetworkRegistry.register performs no namespace validation, so the payload id
        // domum_ornamentum:creative_set_archicutter_slot stays valid in the merged jar.
        final PayloadRegistrar registry = event.registrar(modVersion);

        registry.playToServer(CreativeSetArchitectCutterSlotMessage.ID, CreativeSetArchitectCutterSlotMessage.CODEC, CreativeSetArchitectCutterSlotMessage::onExecute);
    }

    // PORT26-TODO-DATAGEN: re-add the GatherDataEvent.Client handler once the datagen providers
    // are ported to the 26.1.2 vanilla ModelProvider system (blockstates/items/tags/loot/recipes/lang).
}
