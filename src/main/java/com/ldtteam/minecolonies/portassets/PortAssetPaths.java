package com.ldtteam.minecolonies.portassets;

import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Path resolution for the external asset store — PORT26 (publishing support).
 *
 * <p>All externalized upstream assets live under {@code <gamedir>/port-assets/}:</p>
 * <pre>
 *   &lt;gamedir&gt;/port-assets/
 *     settings.json                     loader settings (mode: auto|manual|off)
 *     source-jars/                      drop official 1.21.1 jars here for offline installs
 *     minecolonies/                     one folder per namespace:
 *       assets/&lt;ns&gt;/...                 extracted from the official jar
 *       data/&lt;ns&gt;/...
 *       blueprints/...
 *       pack.mcmeta                     REGENERATED at runtime for the current pack formats
 *       .provisioned.json               marker (source URL, file name, date)
 * </pre>
 *
 * <p>Game directory resolution follows the port's established patterns (no FMLPaths usage
 * anywhere in the port): on the client {@link Minecraft#gameDirectory} of the running game
 * instance; on a dedicated server the working directory, which is the game directory for
 * every vanilla/NeoForge server launch. The client-only lookup is isolated in
 * {@link ClientGameDir} so the {@code Minecraft} class is never touched on a server.</p>
 */
public final class PortAssetPaths
{
    /**
     * Name of the store root folder inside the game directory.
     */
    public static final String ROOT_FOLDER = "port-assets";

    /**
     * Name of the folder where users can drop official jars for offline provisioning.
     */
    public static final String SOURCE_JARS_FOLDER = "source-jars";

    /**
     * Name of the settings file inside the store root.
     */
    public static final String SETTINGS_FILE = "settings.json";

    /**
     * Name of the provisioning marker file inside each namespace folder.
     */
    public static final String MARKER_FILE = ".provisioned.json";

    private PortAssetPaths()
    {
        // static helper only
    }

    /**
     * The game directory (dist-safe — see class javadoc).
     *
     * @return absolute game directory path.
     */
    public static Path gameDir()
    {
        if (FMLEnvironment.getDist().isClient())
        {
            final Path clientDir = ClientGameDir.get();
            if (clientDir != null)
            {
                return clientDir;
            }
        }
        // dedicated server (or very early client edge case): the working directory IS the
        // game directory for every dedicated-server launch.
        return Path.of("").toAbsolutePath();
    }

    /**
     * The external asset store root ({@code <gamedir>/port-assets}).
     *
     * @return store root path (may not exist yet).
     */
    public static Path root()
    {
        return gameDir().resolve(ROOT_FOLDER);
    }

    /**
     * The folder of one namespace ({@code <gamedir>/port-assets/<modId>}).
     *
     * @param modId the namespace/mod id.
     * @return namespace folder path (may not exist yet).
     */
    public static Path namespaceDir(final String modId)
    {
        return root().resolve(modId);
    }

    /**
     * The manual source jar folder ({@code <gamedir>/port-assets/source-jars}).
     *
     * @return source jar folder path (may not exist yet).
     */
    public static Path sourceJarsDir()
    {
        return root().resolve(SOURCE_JARS_FOLDER);
    }

    /**
     * The provisioning marker of one namespace.
     *
     * @param modId the namespace/mod id.
     * @return marker file path (may not exist yet).
     */
    public static Path markerFile(final String modId)
    {
        return namespaceDir(modId).resolve(MARKER_FILE);
    }

    /**
     * Client-only game directory lookup. This class is only ever loaded when
     * {@code FMLEnvironment.getDist().isClient()} — see {@link #gameDir()} — so the
     * {@code Minecraft} reference never loads on a dedicated server.
     */
    private static final class ClientGameDir
    {
        private ClientGameDir()
        {
            // static helper only
        }

        static Path get()
        {
            final Minecraft minecraft = Minecraft.getInstance();
            return minecraft == null ? null : minecraft.gameDirectory.toPath();
        }
    }

    /**
     * Convenience check used by callers that only care about "is anything there at all".
     *
     * @return true if the store root folder exists.
     */
    public static boolean rootExists()
    {
        return Files.isDirectory(root());
    }
}
