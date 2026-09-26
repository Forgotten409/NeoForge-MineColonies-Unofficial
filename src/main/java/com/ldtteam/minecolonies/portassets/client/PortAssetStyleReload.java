package com.ldtteam.minecolonies.portassets.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.ldtteam.minecolonies.MineColonies;
import com.ldtteam.minecolonies.portassets.AssetNamespace;
import com.ldtteam.minecolonies.portassets.AssetProvisioner;
import com.ldtteam.minecolonies.portassets.PortAssetPaths;
import com.ldtteam.minecolonies.portassets.PortAssets;
import com.ldtteam.structurize.storage.ClientStructurePackLoader;
import com.ldtteam.structurize.storage.StructurePacks;
import com.ldtteam.structurize.storage.StructurePackMeta;
import com.ldtteam.structurize.storage.rendering.RenderingCache;

import net.minecraft.client.Minecraft;

/**
 * Hot-loads the provisioned external building styles — PORT26 (play-test #5 fix).
 *
 * <p><b>Why this exists:</b> the client structure-pack discovery
 * ({@code ClientStructurePackLoader.onClientLoading}) runs ONCE at client construction —
 * on a fresh install that is BEFORE the first-run provisioning puts the store on disk, and
 * the publish jar carries no bundled {@code blueprints/} at all (ARR — see
 * docs/PUBLISHING.md §1). The result of the old flow: after [Download now] the textures
 * were active immediately, but the build tool showed no structure packs / styles until a
 * full game restart — exactly what play-test #5 reported ("no styles in publish, they were
 * there in dev": dev builds bundle {@code blueprints/minecolonies/**} in the jar, publish
 * builds do not).</p>
 *
 * <p><b>How:</b> after a successful provisioning run we replay the very same reset the
 * vanilla log-off path performs inside {@code ClientStructurePackLoader.onWorldTick}:
 * {@code loadingState = LOADING}, {@code StructurePacks.clearPacks()},
 * {@code RenderingCache.clear()}, {@code onClientLoading()} again. Re-running the full
 * discovery (instead of only appending the external packs) keeps the vanilla state machine
 * intact: while {@code loadingState == LOADING} the world-entry branch waits, so
 * {@code ensureSelectedPack()} only runs once ALL packs — jar packs, game-folder packs and
 * the newly provisioned external packs — are registered. This only ever runs at the title
 * screen (the notice screen lives there; in-world the player simply keeps playing and the
 * next session finds the store at construction time).</p>
 *
 * <p>{@link #verify()} then compares the pack directories that exist under each external
 * store against the packs actually REGISTERED in {@code StructurePacks} — the caller only
 * reports success when they match, so a failed re-discovery degrades to the honest
 * "restart the game" status line instead of a silent no-op.</p>
 */
public final class PortAssetStyleReload
{
    /** Directory scan depth limit below the extracted blueprints/ root (mirrors PortAssetBlueprints). */
    private static final int MAX_DEPTH = 2;

    private PortAssetStyleReload()
    {
        // static helper only
    }

    /**
     * Restarts the client structure-pack discovery so the provisioned external styles
     * become usable without a game restart.
     *
     * @return true when a re-discovery was started (at least one external store carries a
     *         blueprints folder); false when there is nothing to reload — the caller then
     *         skips straight to the "active" status.
     */
    public static boolean start()
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level != null)
        {
            // only safe/needed at the title screen — in-world the store simply arrives
            // with the next session (discovery re-runs at construction)
            return false;
        }
        if (ClientStructurePackLoader.loadingState == ClientStructurePackLoader.ClientLoadingState.LOADING)
        {
            // a discovery is already running (fresh boot race) — it will pick the store up
            // by itself now that it exists on disk
            return true;
        }
        if (!anyExternalBlueprints())
        {
            return false;
        }

        ClientStructurePackLoader.loadingState = ClientStructurePackLoader.ClientLoadingState.LOADING;
        StructurePacks.clearPacks();
        RenderingCache.clear();
        ClientStructurePackLoader.onClientLoading();
        return true;
    }

    /**
     * Verifies that every external style pack on disk is registered in the live structure
     * pack registry. Call AFTER the re-discovery finished ({@code loadingState} left the
     * LOADING state — its {@code finally} block only sets FINISHED_LOADING after the
     * external scan).
     *
     * @return true when every external blueprints folder is fully represented in
     *         {@code StructurePacks#getPackMetas()}.
     */
    public static boolean verify()
    {
        boolean allRegistered = true;
        for (final AssetNamespace namespace : PortAssets.NAMESPACES)
        {
            final String owner = AssetProvisioner.downloadOwner(namespace);
            final Path blueprintsRoot = PortAssetPaths.namespaceDir(owner).resolve("blueprints");
            if (!Files.isDirectory(blueprintsRoot))
            {
                continue; // not provisioned (or a blueprint-less namespace)
            }

            final int expected = countPackDirs(blueprintsRoot, 0);
            int registered = 0;
            for (final StructurePackMeta meta : StructurePacks.getPackMetas())
            {
                if (meta.getPath() != null && meta.getPath().startsWith(blueprintsRoot))
                {
                    registered++;
                }
            }

            if (registered < expected)
            {
                allRegistered = false;
                MineColonies.LOGGER.warn("port-assets: {} — {} style pack(s) on disk but only {} registered "
                    + "in the live registry; a game restart is required", owner, expected, registered);
            }
            else
            {
                MineColonies.LOGGER.info("port-assets: {} — {} external style pack(s) registered", owner, registered);
            }
        }
        return allRegistered;
    }

    /**
     * @return true when at least one namespace store carries a blueprints folder.
     */
    private static boolean anyExternalBlueprints()
    {
        for (final AssetNamespace namespace : PortAssets.NAMESPACES)
        {
            if (Files.isDirectory(PortAssetPaths.namespaceDir(AssetProvisioner.downloadOwner(namespace)).resolve("blueprints")))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Counts the structure-pack directories (directories directly containing a
     * {@code pack.json}) up to {@link #MAX_DEPTH} levels below the given root — the same
     * layout contract {@code PortAssetBlueprints} scans.
     *
     * @param dir   the directory to scan.
     * @param depth the current depth (root = 0).
     * @return the number of pack directories found.
     */
    private static int countPackDirs(final Path dir, final int depth)
    {
        if (depth > MAX_DEPTH)
        {
            return 0;
        }
        int count = 0;
        try (final Stream<Path> entries = Files.list(dir))
        {
            for (final Path element : entries.toList())
            {
                if (Files.isRegularFile(element.resolve("pack.json")))
                {
                    count++;
                }
                else if (Files.isDirectory(element))
                {
                    count += countPackDirs(element, depth + 1);
                }
            }
        }
        catch (final Exception e)
        {
            MineColonies.LOGGER.warn("port-assets: failed counting style packs at {}: {}", dir, e.toString());
        }
        return count;
    }
}
