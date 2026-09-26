package com.ldtteam.minecolonies.portassets;

import java.util.List;

import com.ldtteam.minecolonies.MineColonies;
import com.ldtteam.minecolonies.portassets.client.PortAssetClientEvents;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

/**
 * External ARR asset loader entry point — PORT26 (publishing support).
 *
 * <p>Registration follows the merged-mod bootstrap pattern of this port (see
 * {@code DomumOrnamentum.init} / {@code Structurize.init}): the single
 * {@code @Mod("minecolonies")} constructor calls {@link #init(IEventBus)}, which registers
 * the mod-bus pack finder ({@link PortAssetPackSources} — {@code AddPackFindersEvent}) and
 * the game-bus handlers (dedicated-server warning; client first-run notice on the client
 * dist only).</p>
 *
 * <h2>Namespace table — why only these</h2>
 * <p>License policy of this port (see docs/PUBLISHING.md §1): of the five merged upstream
 * projects, only <b>MineColonies</b> carries ARR (All Rights Reserved) art/data content —
 * its textures, models, sounds, lang, blueprints and generated data must not be
 * redistributed inside our jar. <b>Structurize, Domum Ornamentum, BlockUI and Multi-Piston
 * ship GPL-3.0 without an ARR asset carve-out</b>, so their assets + data simply stay
 * bundled in every build (dev AND publish) — no provisioning needed.</p>
 *
 * <p>Consequently the loader externalizes FOUR namespaces (all ARR, all fetched at runtime
 * from their official CurseForge channels — never bundled):</p>
 * <ul>
 *   <li><b>minecolonies</b> — {@code minecolonies-1.1.1387-1.21.1-snapshot} (CurseForge /
 *       forgecdn direct link — stable, no auth). Its jar also carries the mixed
 *       {@code data/c/**}, {@code data/dynamictrees/**}, {@code data/neoforge/**} and
 *       {@code data/minecraft/**} contributions (tags merge additively across packs, so
 *       the bundled GPL mods' tag files and the external pack coexist).</li>
 *   <li><b>towntalk</b> — {@code towntalk-1.2.0} (also CurseForge / forgecdn). TownTalk is
 *       the ARR citizen-voice companion; its jar keeps everything under {@code respack/}
 *       (see {@link #NAMESPACES} contentRootPrefix). The official jar's own classes cannot
 *       run on 26.1.2 — see {@link TownTalkCompat} for the bridge.</li>
 *   <li><b>byzantine</b> — {@code Byzantine-1.21.1-51.jar} (CurseForge project 974855,
 *       file 8111937). ARR building-STYLE pack: 4864 {@code .blueprint} files under
 *       {@code blueprints/byzantine/{Byzantine_for_1.20, Nile, Rebel_Base, Shogun}/}.
 *       Lowcodefml mod on CurseForge (zero compiled classes) — for us it is a pure
 *       blueprint source; the style content reaches the game through
 *       {@link PortAssetBlueprints#discoverExternalBlueprints}, not through a resource
 *       pack.</li>
 *   <li><b>stylecolonies</b> — {@code stylecolonies-1.15.59-1.21.1.jar} (CurseForge
 *       project 827507, file 8782133). ARR building-STYLE pack with 13 style sets
 *       ({@code blueprints/stylecolonies/{Antique, Crimson_Keep, Farthest Frontier,
 *       Functional Fantasy, High Magic, Hive, Tropical, aquatica, corrupted, fairytale,
 *       frontier, steampunk, underwaterbase}/}, 5304 blueprints). Its single marker class
 *       is compiled against NeoForge 21.1 and must NEVER be loaded as a mod — the
 *       port-assets pipeline only extracts {@code blueprints/} and never touches classes
 *       (same pattern as TownTalk, but the class is empty boilerplate so no runtime
 *       bridge is needed).</li>
 * </ul>
 *
 * <p>Both style packs use the identical {@code pack.json} + GZIP-NBT {@code .blueprint}
 * format the port itself ships (verified byte-format against our own 25 packs), and all
 * their blueprint {@code required_mods} resolve inside the port's merged mod ids. Their
 * stores carry no {@code assets/}/{@code data/} folders, so {@link PortAssetPackSources}
 * skips resource-pack injection for them (blueprint scan only) — the resource-pack list
 * stays limited to packs that actually contain resources.</p>
 *
 * <p>To refresh an external asset version, update the URL in {@link #NAMESPACES} below
 * (see docs/PUBLISHING.md → "Refreshing asset versions").</p>
 */
public final class PortAssets
{
    /**
     * The namespace table — the single source of truth for what gets provisioned.
     * Only the ARR namespace(s) belong here; GPL-licensed mod namespaces stay bundled.
     */
    public static final List<AssetNamespace> NAMESPACES = List.of(
        new AssetNamespace(
            "minecolonies",
            "MineColonies (textures, models, sounds, lang)",
            "https://mediafilez.forgecdn.net/files/8872/247/minecolonies-1.1.1387-1.21.1-snapshot.jar",
            "minecolonies-1.1.1387-1.21.1-snapshot.jar",
            "https://codeload.github.com/ldtteam/minecolonies/tar.gz/refs/heads/version/1.21",
            null,
            null,
            "MineColonies ARR assets + data + blueprints (10694 blueprint files). The only "
                + "ARR art namespace of the mod family — structurize/domum_ornamentum/blockui/"
                + "multipiston are GPL-3.0 and stay bundled in the jar."),
        new AssetNamespace(
            "towntalk",
            "TownTalk (citizen voices)",
            "https://mediafilez.forgecdn.net/files/5653/504/towntalk-1.2.0.jar",
            "towntalk-1.2.0.jar",
            null,
            null,
            "respack/",
            "TownTalk ARR voice pack — respack/assets/minecolonies/sounds/** (citizen voices "
                + "by job/gender/voice-profile). The official jar's own classes cannot run on "
                + "26.1.2 (ResourceLocation → Identifier); this port re-implements its runtime "
                + "behaviour and uses the jar purely as an asset source — see TownTalkCompat."),
        new AssetNamespace(
            "byzantine",
            "Byzantine Styles Pack (4 building styles)",
            "https://mediafilez.forgecdn.net/files/8111/937/Byzantine-1.21.1-51.jar",
            "Byzantine-1.21.1-51.jar",
            null,
            null,
            null,
            "Byzantine ARR style pack (CurseForge 974855) — blueprints/byzantine/** with the "
                + "Byzantine_for_1.20, Nile, Rebel_Base and Shogun style sets (4864 .blueprint "
                + "files). Zero compiled classes; consumed purely through the external "
                + "blueprints scan."),
        new AssetNamespace(
            "stylecolonies",
            "StyleColonies (13 building styles)",
            "https://mediafilez.forgecdn.net/files/8782/133/stylecolonies-1.15.59-1.21.1.jar",
            "stylecolonies-1.15.59-1.21.1.jar",
            null,
            null,
            null,
            "StyleColonies ARR style pack (CurseForge 827507) — blueprints/stylecolonies/** "
                + "with 13 style sets, 5304 .blueprint files. Its one marker class targets "
                + "NeoForge 21.1 and is never loaded; only the blueprints are extracted."));

    /**
     * Loaded once from {@code <gamedir>/port-assets/settings.json}.
     */
    private static volatile PortAssetSettings settings;

    private PortAssets()
    {
        // static entry point only
    }

    /**
     * Bootstrap — called from the merged mod constructor
     * ({@code com.ldtteam.minecolonies.MineColonies}).
     *
     * @param modBus the mod event bus of the single merged mod.
     */
    public static void init(final IEventBus modBus)
    {
        settings = PortAssetSettings.load();

        // repair hook: "reprovision": true wipes the store once, then re-downloads
        if (settings.reprovision)
        {
            settings.reprovision = false;
            for (final AssetNamespace namespace : NAMESPACES)
            {
                AssetProvisioner.deleteStore(AssetProvisioner.downloadOwner(namespace));
            }
            settings.save();
        }

        // TownTalk runtime bridge: loud warning when the official jar sits ACTIVE in mods/
        TownTalkCompat.warnIfActiveMod();

        // Dedicated-server opt-in auto-provisioning (PORT26 server support): runs during
        // mod construction, i.e. BEFORE the server's pack repository is built — the
        // AddPackFindersEvent handler then sees the store on disk and injects the data
        // packs on this very boot, and ServerStructurePackLoader finds the blueprints.
        // Blocking with log progress is exactly what the server owner opted into with
        // "serverAutoDownload": true; a failure logs loudly and falls back to the manual
        // instructions (the server still boots — just without the external datapacks).
        if (FMLEnvironment.getDist().isDedicatedServer() && settings.serverAutoDownload && !settings.isOff())
        {
            provisionServerAtBoot();
        }

        // mod bus: pack injection (resource pack + data pack) — fires for CLIENT_RESOURCES
        // and SERVER_DATA repository builds.
        modBus.register(PortAssetPackSources.class);

        // game bus (instance registration — the port's battle-tested path):
        NeoForge.EVENT_BUS.register(PortAssetServerWarning.getInstance());
        if (FMLEnvironment.getDist().isClient())
        {
            NeoForge.EVENT_BUS.register(PortAssetClientEvents.getInstance());
        }

        MineColonies.LOGGER.info("port-assets: external asset loader registered (mode: {}, namespaces: {})",
            settings.mode, NAMESPACES.size());
    }

    /**
     * Provisions every missing namespace on a dedicated server (blocking, one log line per
     * progress step). Never throws — each failure is logged and the boot continues.
     */
    private static void provisionServerAtBoot()
    {
        try
        {
            final List<AssetNamespace> missing = AssetProvisioner.missingNamespaces();
            if (missing.isEmpty())
            {
                return;
            }
            MineColonies.LOGGER.info("port-assets: serverAutoDownload=true — provisioning {} namespace(s) now "
                + "(this blocks the server boot for the download): {}",
                missing.size(), missing.stream().map(AssetNamespace::modId).toList());

            int failures = 0;
            for (final AssetNamespace namespace : missing)
            {
                final String error = AssetProvisioner.provision(namespace,
                    line -> MineColonies.LOGGER.info("port-assets: {}", line));
                if (error != null)
                {
                    failures++;
                    MineColonies.LOGGER.warn("port-assets: server provisioning of '{}' FAILED: {} — falling back "
                        + "to the manual install instructions", namespace.modId(), error);
                }
            }
            if (failures == 0)
            {
                MineColonies.LOGGER.info("port-assets: server provisioning finished — data packs and blueprints "
                    + "will be injected on this boot");
            }
            else
            {
                MineColonies.LOGGER.warn("port-assets: {} namespace(s) could not be provisioned — see the manual "
                    + "install instructions below", failures);
            }
        }
        catch (final Throwable t)
        {
            MineColonies.LOGGER.warn("port-assets: server auto-provisioning crashed — continuing the boot "
                + "without the external packs", t);
        }
    }

    /**
     * @return the loaded settings (never null; defaults before {@link #init}).
     */
    public static PortAssetSettings getSettings()
    {
        final PortAssetSettings current = settings;
        return current != null ? current : new PortAssetSettings();
    }

    /**
     * Namespace-table lookup by download owner (the store folder name). Used for
     * human-readable pack descriptions where only the owner id is at hand.
     *
     * @param owner the store owner mod id (namespace mod id, or its providedVia carrier).
     * @return the matching namespace entry, or null for unknown owners.
     */
    public static AssetNamespace byOwner(final String owner)
    {
        for (final AssetNamespace namespace : NAMESPACES)
        {
            if (AssetProvisioner.downloadOwner(namespace).equals(owner))
            {
                return namespace;
            }
        }
        return null;
    }
}
