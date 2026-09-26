package com.ldtteam.minecolonies.portassets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.ldtteam.minecolonies.MineColonies;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Injects every provisioned external asset store as an always-enabled pack — PORT26
 * (publishing support).
 *
 * <h2>Repository-source lifetime — the play-test #4 lesson (IMPORTANT)</h2>
 * <p>NeoForge's {@code ResourcePackLoader.populatePackRepository} fires
 * {@link AddPackFindersEvent} <b>once, when a {@code PackRepository} is created</b>; the
 * sources added via {@code event.addRepositorySource(...)} then live in the repository's
 * permanent source set and are re-queried by every {@code PackRepository#reload()}
 * ({@code discoverAvailable()} → {@code RepositorySource#loadPacks}).</p>
 *
 * <p><b>v1 bug:</b> this handler only added a source for a namespace when the store
 * already existed <i>at repository-creation time</i> — so a store provisioned later in the
 * session (the first-run [Download now] click) had <b>no source in the set at all</b>: the
 * post-download {@code Minecraft#reloadResourcePacks()} reloaded the OLD selection, the new
 * pack stayed invisible in the resource-pack list and only appeared after a full game
 * restart (repository re-created → event re-fired). Exactly what the play-tester saw.</p>
 *
 * <p><b>v2 contract:</b> for EVERY namespace in the table we add its source
 * unconditionally at event time; the source's {@code loadPacks} re-evaluates the store
 * presence <i>on every call</i> (i.e. on every reload — startup, F3+T, and our own
 * post-provisioning reload) and only then builds and emits the {@code Pack}. A store that
 * appears mid-session is therefore discovered by the very next reload, no restart.</p>
 *
 * <h2>APIs verified against the 26.1.2 decompiled reference (mc-classes/26.1.2-full)</h2>
 * <ul>
 *   <li>{@code AddPackFindersEvent} (mod bus, {@code IModBusEvent}) fires for BOTH
 *       {@code PackType.CLIENT_RESOURCES} and {@code PackType.SERVER_DATA} repository
 *       builds — {@code ResourcePackLoader.populatePackRepository} posts it with the
 *       repository's pack type (26.1.2-full source).</li>
 *   <li>{@code PathPackResources.PathResourcesSupplier(Path)} — folder-backed pack.</li>
 *   <li>{@code Pack.readMetaAndCreate(PackLocationInfo, ResourcesSupplier, PackType,
 *       PackSelectionConfig)}.</li>
 *   <li>{@code PackSelectionConfig(required, position, fixedPosition)} — required=true keeps
 *       the pack always enabled (rebuilt into the selection by
 *       {@code PackRepository#rebuildSelected}); {@code Pack.Position.BOTTOM} = low priority,
 *       so the mod's own resources win whenever both provide a file (dev jars that still
 *       bundle assets are unaffected by the external pack).</li>
 *   <li>{@code pack.mcmeta} format range is (re)generated at runtime for the CURRENT game
 *       version by {@link AssetProvisioner#ensurePackMcmeta} — the 1.21.1 jars carry
 *       pack_format 34, which 26.1.2 (resources 84 / data 101.x) would reject.</li>
 * </ul>
 *
 * <p>Blueprint-only stores (the style packs — byzantine, stylecolonies) carry no
 * {@code assets/} or {@code data/} folders; their content reaches the game through
 * {@link PortAssetBlueprints#discoverExternalBlueprints} instead. Those stores are NOT
 * injected as resource/data packs (an empty pack entry in the resource-pack list would only
 * confuse players — see the play-test #4 "two identical packs" report).</p>
 */
public class PortAssetPackSources
{
    /**
     * Pack id prefix — also the folder marker that this is our injected pack.
     */
    public static final String PACK_ID_PREFIX = "port-assets/";

    /**
     * Mod-bus handler for {@link AddPackFindersEvent} (fired for both pack types, once per
     * repository creation — see the class javadoc for the lifetime contract).
     *
     * @param event the event.
     */
    @SubscribeEvent
    public static void onAddPackFinders(final AddPackFindersEvent event)
    {
        final PortAssetSettings settings = PortAssets.getSettings();
        if (settings.isOff() && !anyStorePresent())
        {
            return; // "off" still injects an existing store, but stays silent otherwise
        }

        final PackType packType = event.getPackType();
        for (final AssetNamespace namespace : PortAssets.NAMESPACES)
        {
            // ALWAYS register the source (see class javadoc); the store presence is
            // re-checked on every repository reload inside loadPacks
            event.addRepositorySource(consumer -> {
                try
                {
                    final Pack pack = buildPackIfPresent(namespace, packType);
                    if (pack != null)
                    {
                        consumer.accept(pack);
                    }
                }
                catch (final Throwable t)
                {
                    // never break pack discovery for the rest of the game
                    MineColonies.LOGGER.warn("port-assets: injection of {} failed", namespace.modId(), t);
                }
            });
        }
    }

    /**
     * Builds the injected pack for one namespace — re-evaluated on EVERY repository reload
     * (startup, F3+T, post-provisioning reload), so a store that appears mid-session is
     * picked up without a game restart.
     *
     * @param namespace the namespace table entry.
     * @param packType  the repository's pack type.
     * @return the pack, or null when the store is absent/not yet marked, or when the store
     *         carries no resource/data content (blueprint-only style packs).
     */
    private static Pack buildPackIfPresent(final AssetNamespace namespace, final PackType packType)
    {
        final String owner = AssetProvisioner.downloadOwner(namespace);
        final Path storeDir = PortAssetPaths.namespaceDir(owner);
        if (!Files.isDirectory(storeDir) || !Files.isRegularFile(PortAssetPaths.markerFile(owner)))
        {
            return null; // not provisioned (yet) — silently absent until the next reload
        }

        // blueprint-only stores (style packs) are consumed by the blueprints scan instead
        if (!Files.isDirectory(storeDir.resolve("assets")) && !Files.isDirectory(storeDir.resolve("data")))
        {
            return null;
        }

        try
        {
            // regenerate pack.mcmeta for the CURRENT pack formats before the meta read
            AssetProvisioner.ensurePackMcmeta(owner);

            final PathPackResources.PathResourcesSupplier resources =
                new PathPackResources.PathResourcesSupplier(storeDir);
            final PackLocationInfo location = new PackLocationInfo(
                PACK_ID_PREFIX + owner,
                Component.literal(packTitle(namespace)),
                PackSource.BUILT_IN,
                Optional.empty());
            return Pack.readMetaAndCreate(
                location,
                resources,
                packType,
                new PackSelectionConfig(true, Pack.Position.BOTTOM, true));
        }
        catch (final Throwable t)
        {
            MineColonies.LOGGER.warn("port-assets: could not read pack metadata for {} — skipped", owner, t);
            return null;
        }
    }

    /**
     * The pack title shown in the resource-pack selection list. Deliberately DISTINCT per
     * namespace — play-test #4 reported "two identical resource packs" when both entries
     * shared the "MineColonies Port Assets — …" prefix; these titles say what each pack
     * actually contains.
     *
     * @param namespace the namespace table entry.
     * @return the human-readable pack title.
     */
    public static String packTitle(final AssetNamespace namespace)
    {
        return switch (namespace.modId())
        {
            case "minecolonies" -> "MineColonies Port — Art, Sounds & Data (textures, models, lang)";
            case "towntalk" -> "MineColonies Port — TownTalk Citizen Voices (sound pack)";
            default -> "MineColonies Port — " + namespace.displayName();
        };
    }

    private static boolean anyStorePresent()
    {
        for (final AssetNamespace namespace : PortAssets.NAMESPACES)
        {
            final String owner = AssetProvisioner.downloadOwner(namespace);
            if (Files.isDirectory(PortAssetPaths.namespaceDir(owner)))
            {
                return true;
            }
        }
        return false;
    }
}
