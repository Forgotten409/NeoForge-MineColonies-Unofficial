package com.ldtteam.blockui.mod;

import com.ldtteam.blockui.AtlasManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

/**
 * BlockUI — merged-mod bootstrap — PORT 26.1.2.
 *
 * <p>Originally this was the standalone {@code @Mod("blockui")} entry point. In the merged
 * MineColonies port there is exactly one mod ({@code minecolonies}), so this class only
 * keeps the {@link #MOD_ID} constant (resource locations under {@code blockui:} stay
 * valid in the merged jar) and registers BlockUI's listeners on the buses, called from
 * the single mod constructor.
 */
public class BlockUI
{
    public static final String MOD_ID = "blockui";

    /**
     * Called from the merged minecolonies mod constructor.
     *
     * @param modBus the mod event bus of the single merged mod
     */
    public static void init(final IEventBus modBus)
    {
        if (FMLEnvironment.getDist().isClient())
        {
            modBus.register(ClientLifecycleSubscriber.class);
            modBus.register(AtlasManager.class); // RegisterTextureAtlasesEvent handler
            NeoForge.EVENT_BUS.register(ClientEventSubscriber.class);

            // the merged mod ships the blockui namespace itself
            AtlasManager.registerModAtlas(MOD_ID);
        }
    }
}
