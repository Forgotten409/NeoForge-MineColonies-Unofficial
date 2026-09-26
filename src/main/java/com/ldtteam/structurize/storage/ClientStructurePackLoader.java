package com.ldtteam.structurize.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ldtteam.structurize.Structurize;
import com.ldtteam.structurize.api.Log;
import com.ldtteam.structurize.api.Utils;
import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.network.messages.NotifyServerAboutStructurePacksMessage;
import com.ldtteam.structurize.network.messages.SyncSettingsToServer;
import com.ldtteam.structurize.storage.rendering.RenderingCache;
import com.ldtteam.structurize.util.IOPool;
import com.ldtteam.structurize.util.JavaUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforgespi.language.IModInfo;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.ldtteam.structurize.api.constants.Constants.*;

/**
 * Client side structure pack discovery.
 */
public class ClientStructurePackLoader
{
    /**
     * Different states of the client structure loading progress.
     */
    public enum ClientLoadingState
    {
        LOADING,
        FINISHED_LOADING,
        SYNCING,
        FINISHED_SYNCING
    }

    /**
     * Set after the client finished loading the schematics.
     */
    public static volatile ClientLoadingState loadingState = ClientLoadingState.LOADING;

    /**
     * Called on client mod construction.
     */
    public static void onClientLoading()
    {
        // PORT26 (crash fix #6): keep path -> modId pairs. The old code extracted the pack owner
        // via modPath.toString().split("/")[1], which assumes a POSIX absolute path with 2+
        // segments; for an exploded dev-workspace mod ("mod_resources" pack — the mod file path
        // is a plain directory) findModResource returns a host absolute path, which on Windows
        // uses backslashes: split("/") yields ONE element and [1] throws
        // ArrayIndexOutOfBoundsException, killing the entire client structure pack discovery
        // (9399 blueprints!). The mod id is available right here anyway — use it directly.
        final Map<Path, String> modPaths = new LinkedHashMap<>();
        final List<String> modList = new ArrayList<>();
        for (IModInfo mod : ModList.get().getMods())
        {
            // PORT26: IModFile#findResource(String...) removed → StructurePacks#findModResource
            // (zip NIO filesystem over the mod jar, kept open like the 1.21.1 SecureJar)
            modPaths.put(StructurePacks.findModResource(mod, BLUEPRINT_FOLDER, mod.getModId()), mod.getModId());
            modList.add(mod.getModId());
        }

        if (Minecraft.getInstance() == null)
        {
            // RunData
            return;
        }

        final Path gameFolder = Minecraft.getInstance().gameDirectory.toPath();

        IOPool.execute(() ->
        {
            // PORT26 FIX (batch 24): hardening — any uncaught throwable in this task used to
            // leave loadingState == LOADING forever, which permanently wedged every
            // structurize IO future (they all block on waitUntilFinishedLoading()). The state
            // transition now happens in finally, so a broken discovery degrades to "no packs"
            // with a logged error instead of a dead mod.
            try
            {
                // This loads from the jar
                for (final Map.Entry<Path, String> modPath : modPaths.entrySet())
                {
                    // PORT26 (log noise fix): most mods don't ship a blueprints folder, and
                    // pseudo "mods" (minecraft/neoforge/jei in a dev workspace) resolve to
                    // non-existent paths — that's the normal case, not a failure. Check
                    // existence up front and skip quietly; only real IO errors still warn.
                    if (!java.nio.file.Files.isDirectory(modPath.getKey()))
                    {
                        continue;
                    }
                    try
                    {
                        try (final Stream<Path> paths = Files.list(modPath.getKey()))
                        {
                            paths.forEach(element -> StructurePacks.discoverPackAtPath(element, true, modList, false, modPath.getValue()));
                        }
                    }
                    catch (IOException e)
                    {
                        Log.getLogger().warn("Failed loading packs from mod path: " + modPath.getKey().toString());
                    }
                }

                // Now we load from the main folder.
                try
                {
                    final Path outputPath = gameFolder.resolve(BLUEPRINT_FOLDER);
                    if (!Files.exists(outputPath))
                    {
                        Files.createDirectory(outputPath);
                    }

                    final String safePlayerName = Utils.getSafePackName(Minecraft.getInstance().getUser().getName());
                    final Path clientPackPath = outputPath.resolve(safePlayerName.toLowerCase(Locale.US));
                    if (!Files.exists(clientPackPath))
                    {
                        Files.createDirectory(clientPackPath);
                        Files.createDirectory(clientPackPath.resolve(SCANS_FOLDER));
                        JsonObject jsonObject = new JsonObject();
                        jsonObject.addProperty("version", 1);
                        jsonObject.addProperty("pack-format", 1);
                        jsonObject.addProperty("desc", "This is your local Structurepack. This is where all your scans go.");
                        final JsonArray authorArray = new JsonArray();
                        authorArray.add(Minecraft.getInstance().getUser().getName());
                        jsonObject.add("authors", authorArray);
                        final JsonArray modsArray = new JsonArray();
                        modsArray.add(Constants.MOD_ID);
                        jsonObject.add("mods", modsArray);
                        // PORT26 FIX (batch 24): the pack NAME is the registry key used by
                        // hasPack/getStructurePack everywhere else (scan saving, shape tool,
                        // build tool all query it via Utils.getSafePackName). Using the raw
                        // username here made the pack unreachable whenever the raw name
                        // differed from the safe one (spaces/special chars).
                        jsonObject.addProperty("name", safePlayerName);
                        jsonObject.addProperty("icon",  "");

                        Files.write(clientPackPath.resolve("pack.json"), jsonObject.toString().getBytes());
                    }

                    try (final Stream<Path> paths = Files.list(outputPath))
                    {
                        paths.forEach(element -> StructurePacks.discoverPackAtPath(element, false, modList, false, LOCAL));
                    }
                }
                catch (IOException e)
                {
                    Log.getLogger().warn("Failed loading packs from main folder path: " + gameFolder.toString());
                }

                // PORT26 (publishing support): also load the externally provisioned upstream
                // blueprints from <gamedir>/port-assets/<owner>/blueprints/ (see
                // com.ldtteam.minecolonies.portassets.PortAssetBlueprints).
                com.ldtteam.minecolonies.portassets.PortAssetBlueprints.discoverExternalBlueprints(modList, false);
            }
            catch (final Throwable t)
            {
                Log.getLogger().error("Client structure pack discovery crashed", t);
            }
            finally
            {
                Log.getLogger().warn("Finished discovering Client Structure packs");

                loadingState = ClientLoadingState.FINISHED_LOADING;
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWorldTick(final ClientTickEvent.Pre event)
    {
        if (Minecraft.getInstance().level != null && loadingState == ClientLoadingState.FINISHED_LOADING)
        {
            if (Minecraft.getInstance().isSingleplayer())
            {
                loadingState = ClientLoadingState.FINISHED_SYNCING;
                StructurePacks.setFinishedLoading();
                StructurePacks.ensureSelectedPack();
                return;
            }

            loadingState = ClientLoadingState.SYNCING;
            new NotifyServerAboutStructurePacksMessage(StructurePacks.getPackMetas()).sendToServer();
        }
        else if (Minecraft.getInstance().level == null && (loadingState == ClientLoadingState.SYNCING || loadingState == ClientLoadingState.FINISHED_SYNCING))
        {
            Log.getLogger().warn("Client logged off. Resetting Pack Meta and Reloading State");
            loadingState = ClientLoadingState.LOADING;
            StructurePacks.clearPacks();
            RenderingCache.clear();
            onClientLoading();
        }
    }

    /**
     * On receiving server structure pack update.
     *
     * @param serverStructurePacks the server structure packs.
     */
    public static void onServerSyncAttempt(final Map<String, Double> serverStructurePacks)
    {
        new SyncSettingsToServer().sendToServer();

        if (serverStructurePacks.isEmpty())
        {
            // Most likely single player. Skip.
            loadingState = ClientLoadingState.FINISHED_SYNCING;
            StructurePacks.setFinishedLoading();
            StructurePacks.ensureSelectedPack();
            return;
        }

        // PORT26: GameProfile is a record now → name() instead of getName()
        if (serverStructurePacks.containsKey(Minecraft.getInstance().player.getGameProfile().name()))
        {
            Minecraft.getInstance().player.sendSystemMessage(Component.translatable("structurize.pack.equaluser.error"));
        }

        boolean needsChanges = checkPackDifferences(serverStructurePacks);
        for (final String packKey : serverStructurePacks.keySet())
        {
            if (!StructurePacks.hasPack(packKey))
            {
                needsChanges = true;
                break;
            }
        }

        if (!needsChanges)
        {
            // No new packs have be synced and no updated packs have to be synced.
            loadingState = ClientLoadingState.FINISHED_SYNCING;
            StructurePacks.setFinishedLoading();
            StructurePacks.ensureSelectedPack();
        }
    }

    /**
     * Verify all the incoming server packs against the local ones
     *
     * @param serverStructurePacks the server structure packs.
     */
    private static boolean checkPackDifferences(final Map<String, Double> serverStructurePacks)
    {
        boolean needsChanges = false;
        for (final StructurePackMeta pack : StructurePacks.getPackMetas())
        {
            // Assume the pack is fine by default
            pack.setDisabled(false);

            // Validate the version of the server pack
            final double version = serverStructurePacks.getOrDefault(pack.getName(), -1.0);

            // If the server pack version is -1 it means the server doesn't have this pack
            if (version == -1)
            {
                if (!Structurize.getConfig().getServer().allowPlayerSchematics.get())
                {
                    pack.setDisabled(true);
                }
            }
            // Version on the client is different
            else if (version != pack.getVersion())
            {
                // If the pack is immutable, meaning from a jar, we cannot update it, so it becomes disabled
                if (pack.isImmutable())
                {
                    pack.setDisabled(true);
                }
                // If the pack is mutable, we remove the local copy and tell the server we want to synchronize this pack anew
                // On the next iteration, the client should tell the server it's now missing this pack, which should initiate a copy from the server
                else
                {
                    StructurePacks.removePack(pack.getName());
                    needsChanges = true;
                }
            }
        }
        return needsChanges;
    }

    /**
     * On reception of a new structure pack.
     *
     * @param name the name of the structure pack.
     * @param payload the payload of the pack.
     * @param eol if last sync.
     */
    public static void onStructurePackTransfer(final String name, final ByteBuf payload, final boolean eol)
    {
        final String packName = Utils.getSafePackName(name);
        Log.getLogger().warn("Received Structure pack from the Server: " + packName);
        IOPool.execute(() ->
        {
            final StructurePackMeta pack = StructurePacks.removePack(packName);
            if (pack != null && !pack.isImmutable() && !JavaUtils.deleteDirectory(pack.getPath()))
            {
                Log.getLogger().warn("Error trying to delete pack: ");
            }

            try (ZipInputStream zis = new ZipInputStream(new ByteBufInputStream(payload)))
            {
                ZipEntry zipEntry = zis.getNextEntry();
                final Path structureFolder = Minecraft.getInstance().gameDirectory.toPath().resolve(BLUEPRINT_FOLDER).normalize();
                final Path childPath = structureFolder.resolve(packName);
                if (Files.isSymbolicLink(childPath))
                {
                    return;
                }

                JavaUtils.deleteDirectory(childPath);
                final Path rootPath = Files.createDirectory(childPath);

                while (zipEntry != null)
                {
                    boolean isDirectory = zipEntry.isDirectory();
                    Path newPath = zipSlipProtect(zipEntry, rootPath);

                    if (isDirectory)
                    {
                        Files.createDirectories(newPath);
                    }
                    else
                    {
                        if (newPath.getParent() != null)
                        {
                            if (Files.notExists(newPath.getParent()))
                            {
                                Files.createDirectories(newPath.getParent());
                            }
                        }

                        Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                    }

                    zipEntry = zis.getNextEntry();
                }
                zis.closeEntry();

                final List<String> modList = new ArrayList<>();
                for (IModInfo mod : ModList.get().getMods())
                {
                    modList.add(mod.getModId());
                }

                // now load what we unzipped.
                StructurePacks.discoverPackAtPath(rootPath, true, modList, false, LOCAL);
            }
            catch (final IOException ex)
            {
                Log.getLogger().error("Unable to read datapack from zip", ex);
            }

            payload.release();
            if (eol)
            {
                loadingState = ClientLoadingState.FINISHED_SYNCING;
                StructurePacks.setFinishedLoading();
                StructurePacks.ensureSelectedPack();
            }
        });
    }

    public static Path zipSlipProtect(ZipEntry zipEntry, Path targetDir) throws IOException
    {
        Path targetDirResolved = targetDir.resolve(zipEntry.getName());
        Path normalizePath = targetDirResolved.normalize();
        if (!normalizePath.startsWith(targetDir.normalize()))
        {
            throw new IOException("Bad zip entry: " + zipEntry.getName());
        }

        return normalizePath;
    }

    /**
     * Handles the save message of scans.
     *
     * @param compound compound to store.
     * @param fileName milli seconds for fileName.
     */
    public static void handleSaveScanMessage(final CompoundTag compound, final String fileName, final HolderLookup.Provider provider)
    {
        final String packName = Utils.getSafePackName(Minecraft.getInstance().getUser().getName());
        StructurePacks.switchSelectedPack(StructurePacks.getStructurePack(Utils.getSafePackName(Minecraft.getInstance().getUser().getName())));
        RenderingCache.getOrCreateBlueprintPreviewData("blueprint").setBlueprintFuture(
          StructurePacks.storeBlueprint(packName, compound, Minecraft.getInstance().gameDirectory.toPath()
            .resolve(BLUEPRINT_FOLDER)
            .resolve(packName.toLowerCase(Locale.US))
            .resolve(SCANS_FOLDER).resolve(fileName), provider));
        RenderingCache.getOrCreateBlueprintPreviewData("blueprint").setPos(null);
        // PORT26: displayClientMessage(msg, false) → sendSystemMessage(msg) (no action bar)
        Minecraft.getInstance().player.sendSystemMessage(Component.translatable("Scan successfully saved as %s", fileName));
    }
}
