package com.ldtteam.minecolonies.portassets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.ldtteam.minecolonies.MineColonies;
import com.ldtteam.structurize.storage.StructurePacks;

/**
 * Blueprint loading hook for the external asset store — PORT26 (publishing support).
 *
 * <p>Structurize discovers structure packs from (a) each mod's {@code blueprints/} folder
 * inside its jar and (b) {@code <gamedir>/blueprints/} (see
 * {@code ServerStructurePackLoader.onServerStarting} / {@code ClientStructurePackLoader.onClientLoading}).
 * This hook adds a third source: {@code <gamedir>/port-assets/<owner>/blueprints/}.</p>
 *
 * <p>The upstream 1.21.1 jars nest their blueprints as
 * {@code blueprints/<modId>/<packName>/pack.json}, so the scan looks for directories that
 * directly contain a {@code pack.json} up to two levels below the extracted
 * {@code blueprints/} root (covers both {@code <pack>/pack.json} and
 * {@code <modId>/<pack>/pack.json} layouts, and multiple nested jarJar payloads).</p>
 */
public final class PortAssetBlueprints
{
    /** Directory scan depth limit below the extracted blueprints/ root. */
    private static final int MAX_DEPTH = 2;

    private PortAssetBlueprints()
    {
        // static helper only
    }

    /**
     * Scans the external store for structure packs. Called from the client and server
     * structure pack discovery tasks (on the structurize IO pool), right after the
     * game-folder scan, mirroring how {@code <gamedir>/blueprints/} is handled.
     *
     * @param modList    the loaded mod ids (used by {@link StructurePacks#discoverPackAtPath}
     *                   for the pack's mod requirements).
     * @param clientPack true for the client-side pack registry.
     */
    public static void discoverExternalBlueprints(final List<String> modList, final boolean clientPack)
    {
        for (final AssetNamespace namespace : PortAssets.NAMESPACES)
        {
            final String owner = AssetProvisioner.downloadOwner(namespace);
            final Path blueprintsRoot = PortAssetPaths.namespaceDir(owner).resolve("blueprints");
            if (!Files.isDirectory(blueprintsRoot))
            {
                continue;
            }
            scanForPacks(blueprintsRoot, 0, modList, clientPack, owner);
        }
    }

    /**
     * Recursive (bounded) scan for directories containing a {@code pack.json}.
     */
    private static void scanForPacks(
        final Path dir, final int depth, final List<String> modList, final boolean clientPack, final String owner)
    {
        if (depth > MAX_DEPTH)
        {
            return;
        }
        try (final Stream<Path> entries = Files.list(dir))
        {
            for (final Path element : entries.toList())
            {
                if (Files.isRegularFile(element.resolve("pack.json")))
                {
                    StructurePacks.discoverPackAtPath(element, true, modList, clientPack, owner);
                }
                else if (Files.isDirectory(element))
                {
                    scanForPacks(element, depth + 1, modList, clientPack, owner);
                }
            }
        }
        catch (final IOException e)
        {
            MineColonies.LOGGER.warn("port-assets: failed scanning blueprints at {}: {}", dir, e.toString());
        }
    }
}
