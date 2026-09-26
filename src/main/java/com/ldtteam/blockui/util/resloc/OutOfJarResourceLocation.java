package com.ldtteam.blockui.util.resloc;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Out-of-jar resource locations — PORT 26.1.2 REDESIGN.
 *
 * <p>Originally this class extended {@code ResourceLocation} (with an AT-opened
 * constructor) so it could flow through identifier-typed APIs while secretly carrying an
 * NIO path for files outside the jar (disk files, downloaded skins). In 26.1.2
 * {@code Identifier} is {@code final} with a private constructor, so subclassing is
 * impossible.
 *
 * <p>Port strategy: this is now a <b>utility</b> with a side registry. Methods that used
 * to return {@code OutOfJarResourceLocation} instances (which were also valid
 * {@code ResourceLocation}s) now return plain {@link Identifier}s and register the backing
 * NIO path in {@link #OUT_OF_JAR_PATHS}. Consumers that need the file content use the
 * static readers here ({@link #fileExists}, {@link #openStream}, {@link #openReader})
 * which check the registry first and fall back to the resource manager.
 *
 * <p>PORT26: out-of-jar pixels are loaded into the GPU pipeline through
 * {@code OutOfJarTexture}/{@code SpriteTexture} (both ReloadableTextures), which read
 * from the registered NIO paths and never touch the resource manager.
 */
public final class OutOfJarResourceLocation
{
    /** registered out-of-jar entries: identifier -> nio path */
    private static final Map<Identifier, Path> OUT_OF_JAR_PATHS = new ConcurrentHashMap<>();

    private OutOfJarResourceLocation()
    {
    }

    public static Identifier of(final String namespace, final Path path)
    {
        final Identifier id = Identifier.fromNamespaceAndPath(namespace,
            path.toString().toLowerCase().replace('\\', '/').replaceAll("[^a-z0-9/._-]", "_"));
        OUT_OF_JAR_PATHS.put(id, path);
        return id;
    }

    public static Identifier ofMinecraftFolder(final String namespace, final String... parts)
    {
        Path path = Minecraft.getInstance().gameDirectory.toPath().resolve(namespace);
        for (final String part : parts)
        {
            path = path.resolve(part);
        }
        return of(namespace, path);
    }

    /**
     * Resolves a player skin texture (body/cape/elytra) to the identifier it is registered
     * under in the texture manager.
     *
     * <p>PORT26: the 1.21.1 version sniffed the {@code HttpTexture} cache file and
     * redirected to an out-of-jar NIO path. The 26.1.2 skin system works on
     * {@code ClientAsset.Texture} payloads whose {@code texturePath()} already is a
     * blittable registered texture, so no out-of-jar detour is needed.
     *
     * @param minecraft       minecraft instance
     * @param gameProfile     player profile
     * @param textureSelector null for {@code PlayerSkin#body()}, or {@code PlayerSkin::cape} /
     *                        {@code PlayerSkin::elytra} — both may complete with null
     */
    public static CompletableFuture<Identifier> ofMinecraftSkin(final Minecraft minecraft,
        final GameProfile gameProfile,
        @Nullable final Function<PlayerSkin, ClientAsset.Texture> textureSelector)
    {
        // PORT26: SkinManager#get now completes with Optional<PlayerSkin> (empty while the
        // skin is still loading/missing) and PlayerSkin exposes ClientAsset.Texture parts
        // (body/cape/elytra) whose #texturePath() is the registered texture identifier.
        // The 1.21.1 HttpTexture/NIO-file detour is unnecessary — the texture is already
        // registered under texturePath() and can be blitted directly.
        return minecraft.getSkinManager().get(gameProfile)
            .thenApply(playerSkinOpt -> playerSkinOpt
                .map(playerSkin -> {
                    final ClientAsset.Texture texture = textureSelector == null
                        ? playerSkin.body()
                        : textureSelector.apply(playerSkin);
                    return texture == null ? null : texture.texturePath();
                })
                .orElse(null));
    }

    @Nullable
    public static Path getNioPath(final Identifier resLoc)
    {
        return OUT_OF_JAR_PATHS.get(resLoc);
    }

    public static boolean fileExists(final Identifier resLoc, final ResourceManager fallbackManager)
    {
        final Path nioPath = OUT_OF_JAR_PATHS.get(resLoc);
        if (nioPath != null)
        {
            return Files.exists(nioPath);
        }
        return fallbackManager.getResource(resLoc).isPresent();
    }

    public static InputStream openStream(final Identifier resLoc, final ResourceManager fallbackManager) throws IOException
    {
        final Path nioPath = OUT_OF_JAR_PATHS.get(resLoc);
        if (nioPath != null)
        {
            return Files.newInputStream(nioPath);
        }
        return fallbackManager.open(resLoc);
    }

    public static BufferedReader openReader(final Identifier resLoc, final ResourceManager fallbackManager) throws IOException
    {
        final Path nioPath = OUT_OF_JAR_PATHS.get(resLoc);
        if (nioPath != null)
        {
            return Files.newBufferedReader(nioPath);
        }
        return fallbackManager.openAsReader(resLoc);
    }
}
