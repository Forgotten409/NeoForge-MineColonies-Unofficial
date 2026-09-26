package com.ldtteam.minecolonies.portassets;

import java.util.List;

import com.ldtteam.minecolonies.MineColonies;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Dedicated-server one-time warning — PORT26 (publishing support).
 *
 * <p>Dedicated servers never auto-download <b>by default</b> (headless, unattended, and
 * the machine may be offline). When a required namespace is neither bundled in the
 * running jar nor provisioned in {@code <gamedir>/port-assets/}, a single clear WARNING
 * with the manual instructions is logged per server start. Data pack injection itself
 * still works whenever the folder exists — see {@link PortAssetPackSources}.</p>
 *
 * <p>Server owners who WANT boot-time downloads can set {@code "serverAutoDownload": true}
 * in {@code <gamedir>/port-assets/settings.json} — see {@link PortAssetSettings}; the
 * provisioning then runs during mod construction (before the pack repositories are built)
 * and this warning disappears because nothing is missing anymore.</p>
 */
public class PortAssetServerWarning
{
    private static final PortAssetServerWarning INSTANCE = new PortAssetServerWarning();

    /**
     * @return the singleton instance (registered on {@code NeoForge.EVENT_BUS}).
     */
    public static PortAssetServerWarning getInstance()
    {
        return INSTANCE;
    }

    private PortAssetServerWarning()
    {
    }

    @SubscribeEvent
    public void onServerAboutToStart(final ServerAboutToStartEvent event)
    {
        // the integrated server is covered by the client notice screen flow
        if (!FMLEnvironment.getDist().isDedicatedServer())
        {
            return;
        }

        final List<AssetNamespace> missing = AssetProvisioner.missingNamespaces();
        if (missing.isEmpty())
        {
            return;
        }

        final StringBuilder names = new StringBuilder();
        for (final AssetNamespace namespace : missing)
        {
            if (names.length() > 0)
            {
                names.append(", ");
            }
            names.append(namespace.modId());
        }

        MineColonies.LOGGER.warn("=====================================================================");
        MineColonies.LOGGER.warn(" MINECOLONIES PORT — MISSING EXTERNAL ASSETS ({})", names);
        MineColonies.LOGGER.warn(" This build of the mod does not bundle the upstream art/data");
        MineColonies.LOGGER.warn(" assets (ARR — see the port's README on GitHub). The game will");
        MineColonies.LOGGER.warn(" run, but datapack content (recipes, loot tables, tags) of the");
        MineColonies.LOGGER.warn(" listed namespaces is missing until they are provided.");
        MineColonies.LOGGER.warn(" >>> OPTION A — AUTOMATIC (recommended for online servers):");
        MineColonies.LOGGER.warn("  Set \"serverAutoDownload\": true in <game-dir>/port-assets/");
        MineColonies.LOGGER.warn("  settings.json (create the file if needed) and restart — the");
        MineColonies.LOGGER.warn("  server then downloads+extracts everything at boot, with the");
        MineColonies.LOGGER.warn("  progress logged here (~178 MB total, one time only).");
        MineColonies.LOGGER.warn(" >>> OPTION B — MANUAL INSTALL (offline servers):");
        MineColonies.LOGGER.warn("  1. Download the official jars from their official distribution");
        MineColonies.LOGGER.warn("     channels (CurseForge).");
        MineColonies.LOGGER.warn("  2. Put the jar files into:  <game-dir>/port-assets/source-jars/");
        MineColonies.LOGGER.warn("     (the game dir is the folder containing 'mods' and 'world').");
        MineColonies.LOGGER.warn("  3. On the client: launch once and press 'Download now' on the");
        MineColonies.LOGGER.warn("     first-run screen — this extracts everything automatically.");
        MineColonies.LOGGER.warn("     On the server: copy the extracted folders from a client's");
        MineColonies.LOGGER.warn("     <game-dir>/port-assets/ into this server's <game-dir>/port-assets/");
        MineColonies.LOGGER.warn("     (or extract the jars' assets/, data/ and blueprints/ folders");
        MineColonies.LOGGER.warn("     yourself and restart the server).");
        MineColonies.LOGGER.warn("=====================================================================");
    }
}
