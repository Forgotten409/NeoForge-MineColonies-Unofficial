package com.ldtteam.domumornamentum;

import com.ldtteam.domumornamentum.api.DomumOrnamentumAPI;
import com.ldtteam.domumornamentum.entity.block.ModBlockEntityTypes;
import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.ModCreativeTabs;
import com.ldtteam.domumornamentum.component.ModDataComponents;
import com.ldtteam.domumornamentum.container.ModContainerTypes;
import com.ldtteam.domumornamentum.recipe.ModRecipeSerializers;
import com.ldtteam.domumornamentum.recipe.ModRecipeTypes;
import com.ldtteam.domumornamentum.util.Constants;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Domum Ornamentum — merged-mod bootstrap — PORT 26.1.2.
 *
 * <p>Originally this was the standalone {@code @Mod("domum_ornamentum")} entry point. In the merged
 * MineColonies port there is exactly one mod ({@code minecolonies}), so this class only keeps the
 * {@link #MOD_ID} constant (resource locations under {@code domum_ornamentum:} stay valid in the
 * merged jar) and registers Domum's registries + listeners, called from the single mod constructor.
 */
public class DomumOrnamentum
{
    public static final String MOD_ID = Constants.MOD_ID;
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    /**
     * Called from the merged minecolonies mod constructor.
     *
     * @param modBus the mod event bus of the single merged mod
     */
    public static void init(final IEventBus modBus)
    {
        IDomumOrnamentumApi.Holder.setInstance(DomumOrnamentumAPI.getInstance());

        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.ITEMS.register(modBus);
        ModBlockEntityTypes.BLOCK_ENTITIES.register(modBus);
        ModContainerTypes.CONTAINERS.register(modBus);
        ModRecipeTypes.RECIPES.register(modBus);
        ModRecipeSerializers.SERIALIZERS.register(modBus);
        ModCreativeTabs.TAB_REG.register(modBus);
        ModDataComponents.REGISTRY.register(modBus);

        modBus.register(com.ldtteam.domumornamentum.event.handlers.ModBusEventHandler.class);

        // PORT26 (JEI batch): Architect's Cutter recipe sync. Since 1.21.2 the client no longer
        // receives full Recipe objects (only recipe displays), so mods must explicitly ask
        // NeoForge to sync the recipe types they need client-side. The server handler below is
        // common code (registered on both sides, only ever fires on the server); the client
        // handler stores what arrives and feeds the JEI plugin. Both are JEI-free on purpose —
        // see ClientCutterRecipeCache.
        NeoForge.EVENT_BUS.register(com.ldtteam.domumornamentum.jei.CutterRecipeServerSyncHandler.getInstance());

        if (FMLEnvironment.getDist().isClient())
        {
            // PORT26: manual registration instead of @EventBusSubscriber(modid="domum_ornamentum")
            // — the merged mod's id is "minecolonies", annotation scanning would not find us.
            // PORT26 (batch 10): game-bus handlers are registered as INSTANCES (the most
            // battle-tested path). Static class registration on the game bus was suspect — the
            // tick counter was observed frozen (preview cycling stuck on one material), which
            // also silenced this handler pair.
            NeoForge.EVENT_BUS.register(com.ldtteam.domumornamentum.client.event.handlers.ClientTickEventHandler.getInstance());
            NeoForge.EVENT_BUS.register(com.ldtteam.domumornamentum.client.event.handlers.MateriallyTexturedBlockPreviewRenderHandler.getInstance());
            NeoForge.EVENT_BUS.register(com.ldtteam.domumornamentum.jei.CutterRecipeClientSyncHandler.getInstance());
            modBus.register(com.ldtteam.domumornamentum.client.event.handlers.ModBusEventHandler.class);
        }

        LOGGER.info("Domum Ornamentum merged-mod bootstrap complete (PHASE 2 port)");
    }
}
