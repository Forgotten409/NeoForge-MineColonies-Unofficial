package com.ldtteam.structurize;

import com.ldtteam.structurize.blueprints.v1.DataFixerUtils;
import com.ldtteam.structurize.blueprints.v1.DataVersion;
import com.ldtteam.structurize.component.ModDataComponents;
import com.ldtteam.structurize.config.ClientConfiguration;
import com.ldtteam.structurize.config.ServerConfiguration;
import com.ldtteam.common.config.Configurations;
import com.ldtteam.common.language.LanguageHandler;
import com.ldtteam.structurize.api.Log;
import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.blocks.ModBlocks;
import com.ldtteam.structurize.event.ClientEventSubscriber;
import com.ldtteam.structurize.event.ClientLifecycleSubscriber;
import com.ldtteam.structurize.event.EventSubscriber;
import com.ldtteam.structurize.event.LifecycleSubscriber;
import com.ldtteam.structurize.items.ModItemGroups;
import com.ldtteam.structurize.items.ModItems;
import com.ldtteam.structurize.blockentities.ModBlockEntities;
import com.ldtteam.structurize.storage.ClientFutureProcessor;
import com.ldtteam.structurize.storage.ServerFutureProcessor;
import com.ldtteam.structurize.storage.ClientStructurePackLoader;
import com.ldtteam.structurize.storage.ServerStructurePackLoader;
import com.ldtteam.structurize.storage.rendering.ServerPreviewDistributor;
import net.minecraft.util.datafix.DataFixers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Mod main class — PORT26 merged-mod bootstrap.
 *
 * <p>Originally the standalone {@code @Mod("structurize")} entry point (constructor took
 * {@code FMLModContainer}). In the merged MineColonies port there is exactly one mod
 * ({@code minecolonies}), so this class is a plain bootstrap invoked from the single mod
 * constructor via {@link #init(IEventBus, ModContainer)} — same pattern as
 * {@code MultiPiston.init}. The registry namespace {@code structurize:} stays valid because
 * all DeferredRegisters were created with {@link Constants#MOD_ID}.</p>
 *
 * <p>PORT26: the 1.21.1 constructor parameter {@code Dist dist} was dropped (unused) and
 * {@code FMLModContainer} became {@link ModContainer} (the Configurations ctor takes the
 * base type; the merged mod constructor provides it directly).</p>
 */
public class Structurize
{
    /**
     * The config instance.
     */
    private static Configurations<ClientConfiguration, ServerConfiguration, ?> config;

    /**
     * Mod init, registers events to their respective busses.
     *
     * @param modBus       the mod event bus of the single merged mod
     * @param modContainer the merged mod container (config registration)
     */
    public static void init(final IEventBus modBus, final ModContainer modContainer)
    {
        final IEventBus forgeBus = NeoForge.EVENT_BUS;

        LanguageHandler.loadLangPath("assets/structurize/lang/%s.json");
        // PORT26-merge: namespace "structurize" keeps the original config file names
        // (structurize-client.toml / -server.toml) — one shared ModContainer would
        // otherwise collide with minecolonies' configs in ConfigTracker.
        config = new Configurations<>(modContainer, modBus, "structurize", ClientConfiguration::new, ServerConfiguration::new, null);

        ModDataComponents.REGISTRY.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModItemGroups.TAB_REG.register(modBus);

        modBus.register(LifecycleSubscriber.class);
        forgeBus.register(EventSubscriber.class);

        // PORT26: FMLEnvironment.dist (field) -> FMLEnvironment.getDist()
        if (FMLEnvironment.getDist().isClient())
        {
            modBus.register(ClientLifecycleSubscriber.class);
            forgeBus.register(ClientEventSubscriber.class);

            forgeBus.register(ClientStructurePackLoader.class);
            forgeBus.register(ClientFutureProcessor.class);
        }

        forgeBus.register(ServerStructurePackLoader.class);
        forgeBus.register(ServerFutureProcessor.class);

        forgeBus.register(ServerPreviewDistributor.class);

        if (DataFixerUtils.isVanillaDF)
        {
            if ((DataFixers.getDataFixer().getSchema(Integer.MAX_VALUE - 1).getVersionKey()) >= DataVersion.UPCOMING.getDataVersion() * 10)
            {
                throw new RuntimeException("You are trying to run old mod on much newer vanilla. Missing some newest data versions. Please update com/ldtteam/structures/blueprints/v1/DataVersion");
            }
            // PORT26: FMLEnvironment.isProduction() (field) -> FMLEnvironment.isProduction()
            else if (!FMLEnvironment.isProduction() && DataVersion.CURRENT == DataVersion.UPCOMING)
            {
                throw new RuntimeException("Missing some newest data versions. Please update src/main/java/com/ldtteam/structurize/blueprints/v1/DataVersion.java");
            }
        }
        else
        {
            Log.getLogger().error("----------------------------------------------------------------- \n "
                                    + "Invalid DataFixer detected, schematics might not paste correctly! \n"
                                    +  "The following DataFixer was added: " + DataFixers.getDataFixer().getClass() + "\n"
                                    + "-----------------------------------------------------------------");
        }
    }

    /**
     * Get the config handler.
     *
     * @return the config handler.
     */
    public static Configurations<ClientConfiguration, ServerConfiguration, ?> getConfig()
    {
        return config;
    }
}
