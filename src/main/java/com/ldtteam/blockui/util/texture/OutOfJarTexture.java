package com.ldtteam.blockui.util.texture;

import com.google.gson.JsonObject;
import com.ldtteam.blockui.util.resloc.OutOfJarResourceLocation;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Out-of-jar texture loading, ported to the 26.1.2 GPU texture pipeline.
 *
 * <p>Extends {@link ReloadableTexture} so the texture manager drives the full lifecycle:
 * the initial upload happens through {@code registerAndLoad} and the backing file is
 * automatically re-read on resource reloads (F3+T) — the 26.1.2 equivalent of the old
 * {@code AbstractTexture#load(ResourceManager)} lifecycle.
 *
 * <p>Pixels are read from the NIO path registered in {@link OutOfJarResourceLocation}.
 * The vanilla resource manager is never consulted for out-of-jar identifiers — their
 * {@code ./}/{@code ../}-style paths are rejected by the 26.1.2 pack path normalization,
 * which is why these textures must bypass {@code SimpleTexture} entirely.
 */
public class OutOfJarTexture extends ReloadableTexture
{
    /**
     * Textures we registered ourselves: identifier -> instance. The 26.1.2
     * {@link TextureManager} has no peek-only accessor ({@code getTexture} auto-registers
     * a {@code SimpleTexture} on miss, which would fail on ./-style paths), so we track
     * our own registrations to stay idempotent without triggering that fallback.
     */
    private static final Map<Identifier, AbstractTexture> OUR_TEXTURES = new ConcurrentHashMap<>();

    public OutOfJarTexture(final Identifier resourceLocation)
    {
        super(resourceLocation);
    }

    @Override
    public TextureContents loadContents(final ResourceManager resourceManager) throws IOException
    {
        final Path nioPath = OutOfJarResourceLocation.getNioPath(resourceId());
        if (nioPath == null || !Files.exists(nioPath))
        {
            throw new FileNotFoundException(resourceId().toString());
        }

        try (InputStream is = Files.newInputStream(nioPath))
        {
            // metadata: null is honored by TextureContents (no blur/clamp filtering) —
            // out-of-jar sources have no resource-manager metadata to read
            return new TextureContents(NativeImage.read(is), null);
        }
    }

    public static AbstractTexture assertLoadedDefaultManagers(final Identifier resLoc)
    {
        return assertLoaded(resLoc, Minecraft.getInstance().getTextureManager(), Minecraft.getInstance().getResourceManager());
    }

    /**
     * Checks whether given resLoc should be loaded into given textureManager as outOfJar or sprite texture
     *
     * @return valid texture instance (including missing texture)
     */
    public static AbstractTexture assertLoaded(final Identifier resLoc, final TextureManager textureManager, final ResourceManager resourceManager)
    {
        if (OutOfJarResourceLocation.getNioPath(resLoc) == null)
        {
            // not out-of-jar: normal vanilla systems
            return textureManager.getTexture(resLoc);
        }

        final AbstractTexture existing = OUR_TEXTURES.get(resLoc);
        if (existing != null)
        {
            return existing;
        }

        // animated sources (sibling .mcmeta with "animation") go through the ticking
        // sprite path, everything else is a plain static out-of-jar texture
        final ReloadableTexture texture = hasAnimationMetadata(resLoc) ? new SpriteTexture(resLoc) : new OutOfJarTexture(resLoc);
        textureManager.registerAndLoad(resLoc, texture);
        OUR_TEXTURES.put(resLoc, texture);
        return texture;
    }

    /**
     * @return true if the out-of-jar source behind resLoc declares an animation section
     */
    public static boolean hasAnimationMetadata(final Identifier resLoc)
    {
        final JsonObject mcmeta = readSiblingMcmeta(resLoc);
        return mcmeta != null && mcmeta.has("animation");
    }

    /**
     * Reads the {@code <file>.mcmeta} sibling of an out-of-jar texture. The sibling path
     * is derived from the registered NIO path — the resource manager must not be consulted
     * because out-of-jar identifiers contain path segments it rejects.
     *
     * @return parsed json or null when absent/unparseable
     */
    @Nullable
    public static JsonObject readSiblingMcmeta(final Identifier resLoc)
    {
        final Path nioPath = OutOfJarResourceLocation.getNioPath(resLoc);
        if (nioPath == null)
        {
            return null;
        }

        final Path mcmetaPath = nioPath.resolveSibling(nioPath.getFileName() + ".mcmeta");
        if (!Files.exists(mcmetaPath))
        {
            return null;
        }

        try (var reader = Files.newBufferedReader(mcmetaPath))
        {
            return GsonHelper.parse(reader);
        }
        catch (final Exception e)
        {
            return null;
        }
    }
}
