package com.ldtteam.multipiston;

import com.ldtteam.multipiston.network.MultiPistonChangeMessage;
import com.ldtteam.minecolonies.MineColonies;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.ldtteam.multipiston.ModBlocks.BLOCKS;
import static com.ldtteam.multipiston.ModBlocks.ITEMS;
import static com.ldtteam.multipiston.ModTileEntities.TILE_ENTITIES;

/**
 * MultiPiston — merged-mod bootstrap — PORT 26.1.2.
 *
 * <p>Originally the standalone {@code @Mod("multipiston")} entry point (source: piston-unlimited,
 * the renamed MultiPiston repo). In the merged MineColonies port there is exactly one mod
 * ({@code minecolonies}), so this class only keeps the {@link #MOD_ID} constant (registry
 * namespace {@code multipiston:} stays valid in the merged jar) and registers MultiPiston's
 * registries + network, called from the single mod constructor.
 */
public class MultiPiston
{
    // PORT26: MOD_ID must be declared BEFORE LOGGER — Java forbids reading a static field
    // in an initializer when the field is declared further down (illegal forward reference).
    public static final String                            MOD_ID  = "multipiston";

    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static final DeferredRegister<CreativeModeTab> TAB_REG = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GENERAL = TAB_REG.register("general", () -> CreativeModeTab.builder()
      .icon(() -> new ItemStack(ModBlocks.multipiston.get()))
      .title(Component.translatable("block.multipiston.multipistonblock"))
      .displayItems((config, output) -> output.accept(ModBlocks.multipiston.get()))
      .build());

    /**
     * Called from the merged minecolonies mod constructor.
     *
     * @param modBus the mod event bus of the single merged mod
     */
    public static void init(final IEventBus modBus)
    {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TILE_ENTITIES.register(modBus);
        TAB_REG.register(modBus);

        modBus.register(NetworkHandler.class);
        LOGGER.info("MultiPiston merged-mod bootstrap complete (PHASE 5 port)");
    }

    /**
     * Mod-bus payload registration — merged into a nested class so the multipiston package
     * keeps a single bootstrap entry point.
     */
    public static class NetworkHandler
    {
        @SubscribeEvent
        public static void onNetworkRegistry(final RegisterPayloadHandlersEvent event)
        {
            // PORT26 (merged single-mod): the only mod container is "minecolonies" — a lookup
            // by "multipiston" returns an empty Optional. Also, registrar(String) now takes
            // the network VERSION directly (the old namespace-scoped registrar +
            // .versioned() chain was removed in 26.1).
            final String modVersion = ModList.get()
              .getModContainerById(MineColonies.MOD_ID)
              .map(container -> container.getModInfo().getVersion().toString())
              .orElse("1");

            final PayloadRegistrar registry = event.registrar(modVersion);

            MultiPistonChangeMessage.TYPE.register(registry);
        }
    }
}
