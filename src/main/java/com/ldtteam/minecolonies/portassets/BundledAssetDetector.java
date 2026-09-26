package com.ldtteam.minecolonies.portassets;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonParser;
import com.ldtteam.minecolonies.MineColonies;
import com.ldtteam.structurize.storage.StructurePacks;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * Detects whether the currently running mod jar already bundles a given asset namespace —
 * PORT26 (publishing support).
 *
 * <h2>v2 — deterministic build-variant flag (play-test regression fix)</h2>
 * <p>v1 of this detector probed the ART trees ({@code assets/<ns>/textures|models|sounds|
 * blockstates}) directly, which was correct while the publish jar shipped <b>no</b> art at
 * all under {@code assets/minecolonies/}. The port later started shipping small sets of
 * port-authored 26.1-format art in the publish jar (item egg textures, equipment models,
 * leather) — and the probe began reporting the PUBLISH jar as "bundled", silently disabling
 * the whole provisioning flow for minecolonies. The play-test result: the loader offered to
 * provision ONLY the other namespace (towntalk), never downloaded the minecolonies art, and
 * the game then hard-crashed on the unbound {@code minecraft:damage_type} registry (the
 * damage-type JSONs live in the external store) with no textures and no lang.</p>
 *
 * <p>v2 therefore probes a <b>build-variant flag file</b> instead of any content path:</p>
 * <ul>
 *   <li><b>DEV builds</b> ({@code gradlew build} — full upstream resources from
 *       {@code src/main/resources}) ship {@code portassets/bundled-flag.json}, an explicit
 *       manifest of the namespaces whose full ARR art is bundled in that jar. It sits
 *       deliberately OUTSIDE {@code assets/} and {@code data/} so no pack scanner ever
 *       sees it.</li>
 *   <li><b>The legally-clean publish jar</b> never contains the flag: {@code publishJar}
 *       assembles {@code portassets/**} exclusively from {@code src/main/resources-publish}
 *       and explicitly excludes the dev flag — so the detector answers "not bundled" and
 *       the runtime download flow is offered, no matter how much port-authored art the
 *       publish jar grows over time.</li>
 * </ul>
 *
 * <p>The probe goes through the mod file itself (zip NIO filesystem over the packaged jar /
 * exploded directory, via the port's own {@link StructurePacks#findModResource}) — equivalent
 * to a classpath lookup but works on BOTH dists and at ANY lifecycle phase. The result is
 * cached per namespace for the lifetime of the game. Failure semantics: every error path
 * answers "not bundled", i.e. the worst case is a redundant download offer — never a
 * silently-disabled provisioning flow.</p>
 */
public final class BundledAssetDetector
{
    private static final Map<String, Boolean> CACHE = new ConcurrentHashMap<>();

    private BundledAssetDetector()
    {
        // static helper only
    }

    /**
     * Checks whether the running mod jar itself provides the namespace's assets.
     *
     * @param namespace the asset namespace (e.g. {@code minecolonies}, {@code blockui}).
     * @return true when the running jar is a DEV build that bundles the namespace's full
     *         art (flag manifest present and listing the namespace); false for the
     *         legally-clean publish jar — external provisioning is then offered.
     */
    public static boolean isNamespaceBundled(final String namespace)
    {
        return CACHE.computeIfAbsent(namespace, BundledAssetDetector::probe);
    }

    private static boolean probe(final String namespace)
    {
        try
        {
            // Wildcard is REQUIRED: ModList#getModContainerById returns Optional<? extends
            // ModContainer> (NeoForge 26.1.2) — Optional<ModContainer> would not compile.
            final Optional<? extends ModContainer> container = ModList.get().getModContainerById(MineColonies.MOD_ID);
            if (container.isEmpty())
            {
                return false;
            }
            final IModInfo mod = container.get().getModInfo();

            // Build-variant flag: shipped by DEV builds only (see class javadoc for why an
            // explicit flag replaced the old art-directory probe).
            final Path flag = StructurePacks.findModResource(mod, "portassets", "bundled-flag.json");
            if (flag == null || !Files.isRegularFile(flag))
            {
                return false;
            }
            final var bundled = JsonParser.parseString(Files.readString(flag, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("bundled");
            for (final var entry : bundled)
            {
                if (namespace.equals(entry.getAsString()))
                {
                    return true;
                }
            }
            return false;
        }
        catch (final Throwable t)
        {
            // Never let the detector crash the game — treat as "not bundled" (worst case the
            // user gets offered a redundant download).
            MineColonies.LOGGER.warn("port-assets: bundled-asset detection failed for {}: {}",
                namespace, t.toString());
            return false;
        }
    }
}
