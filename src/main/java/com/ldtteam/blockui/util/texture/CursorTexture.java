package com.ldtteam.blockui.util.texture;

import com.google.gson.JsonObject;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.mod.BlockUI;
import com.ldtteam.blockui.util.cursor.Cursor;
import com.ldtteam.blockui.util.cursor.CursorUtils;
import com.ldtteam.blockui.util.resloc.OutOfJarResourceLocation;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Used for textured cursors.
 *
 * @see Pane#setCursor(Cursor)
 */
public class CursorTexture extends AbstractTexture
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CursorTexture.class);
    private final Identifier resourceLocation;

    private int hotspotX = 0;
    private int hotspotY = 0;
    private long glfwCursorAddress = 0;
    @Nullable
    protected NativeImage nativeImage = null;

    public CursorTexture(final Identifier resLoc)
    {
        this.resourceLocation = resLoc;
    }

    /**
     * Sets cursor hotspot. Hotspot is position in the image which should be used as 0,0 when rendering the cursor (eg. image with
     * 24x24 resolution will be centered on mouse point with hotspot 12x12).
     * 
     * @param x hotspot left offset
     * @param y hotspot top offset
     */
    public void setHotspot(final int x, final int y)
    {
        if (hotspotX != x || hotspotY != y)
        {
            hotspotX = x;
            hotspotY = y;
            onDataChange();
        }
    }

    /**
     * @return true if this is current cursor image, false otherwise
     */
    public boolean isCursorNow()
    {
        return CursorUtils.isCurrentCursor(glfwCursorAddress) && glfwCursorAddress != 0;
    }

    private void onDataChange()
    {
        if (!RenderSystem.isOnRenderThread())
        {
            // PORT26: RenderSystem.recordRenderCall does not exist anymore —
            // queueFencedTask is the render-thread task queue replacement.
            RenderSystem.queueFencedTask(this::onDataChange);
            return;
        }

        if (isCursorNow())
        {
            destroyCursorHandle();
            setCursor();
        }
        else
        {
            destroyCursorHandle();
        }
    }

    protected void destroyCursorHandle()
    {
        if (glfwCursorAddress != 0)
        {
            RenderSystem.assertOnRenderThread();
            if (isCursorNow())
            {
                CursorUtils.resetCursor();
            }

            GLFW.glfwDestroyCursor(glfwCursorAddress);
            glfwCursorAddress = 0;
        }
    }

    /**
     * Sets this texture as cursor image. Resets to default if anything went wrong during setup of this texture.
     */
    public void setCursor()
    {
        if (glfwCursorAddress == 0 && nativeImage != null)
        {
            RenderSystem.assertOnRenderThread();
            try (var stack = MemoryStack.stackPush())
            {
                final GLFWImage image = GLFWImage.malloc(stack);
                image.width(nativeImage.getWidth());
                image.height(nativeImage.getHeight());
                // PORT26: NativeImage#pixels is private in 26.1.2 — copy the pixel data
                // through the public accessors instead. getPixelsABGR() returns the raw
                // ABGR ints which, written in native byte order, produce the RGBA byte
                // sequence GLFW expects. glfwCreateCursor copies the buffer, so it can be
                // freed right after the call.
                final int[] pixels = nativeImage.getPixelsABGR();
                final ByteBuffer buffer = MemoryUtil.memAlloc(pixels.length * 4);
                try
                {
                    for (final int pixel : pixels)
                    {
                        buffer.putInt(pixel);
                    }
                    buffer.flip();
                    MemoryUtil.memPutAddress(image.address() + GLFWImage.PIXELS, MemoryUtil.memAddress(buffer));
                    glfwCursorAddress = GLFW.glfwCreateCursor(image, hotspotX, hotspotY);
                }
                finally
                {
                    MemoryUtil.memFree(buffer);
                }
            }

            if (glfwCursorAddress == 0)
            {
                LOGGER.error("Cannot create textured cursor for resource location: " + resourceLocation);
            }
        }

        if (glfwCursorAddress != 0)
        {
            CursorUtils.setCursorAddress(glfwCursorAddress);
        }
        else
        {
            CursorUtils.resetCursor();
        }
    }

    /**
     * PORT26: AbstractTexture no longer has a {@code load(ResourceManager)} method in 26.1.2
     * (the GpuTexture reload system uses {@code ReloadableTexture#loadContents} instead —
     * incompatible with our out-of-jar fallback loading). This is now a plain method that
     * must be invoked manually after registration (see CursorUtils#loadCursorTexture);
     * CursorUtils re-invokes it on every load request, which doubles as the
     * resource-reload refresh.
     */
    public void load(final ResourceManager resourceManager) throws IOException
    {
        if (nativeImage != null)
        {
            close();
        }

        try (var is = OutOfJarResourceLocation.openStream(resourceLocation, resourceManager))
        {
            nativeImage = NativeImage.read(is);
        }

        // PORT26: metadata sections moved to the MetadataSectionType system in 26.1.2;
        // instead of registering a custom type we read the sibling .mcmeta file directly.
        // Any malformed metadata degrades to the default hotspot (0, 0).
        try (var reader = OutOfJarResourceLocation.openReader(
            Identifier.fromNamespaceAndPath(resourceLocation.getNamespace(), resourceLocation.getPath() + ".mcmeta"),
            resourceManager))
        {
            final JsonObject json = GsonHelper.parse(reader);
            final JsonObject section = GsonHelper.getAsJsonObject(json, "ldtteam." + BlockUI.MOD_ID + ".cursor", null);
            if (section != null)
            {
                // manual set to avoid double onDataChange call
                this.hotspotX = GsonHelper.getAsInt(section, "hotspot.x", 0);
                this.hotspotY = GsonHelper.getAsInt(section, "hotspot.y", 0);
            }
        }
        catch (final IOException | RuntimeException ignored)
        {
            // no mcmeta file (or unparseable one) — default hotspot (0, 0)
        }

        if (nativeImage.format() != Format.RGBA)
        {
            LOGGER.error("Cannot load texture for cursor as it is not in RGBA format, resource location: " + resourceLocation);
            close();
        }

        onDataChange();
    }

    @Override
    public void close()
    {
        destroyCursorHandle();
        if (nativeImage != null)
        {
            nativeImage.close();
            nativeImage = null;
        }
    }

    public static record CursorMetadataSection(int hotspotX, int hotspotY)
    {
        public static final String SECTION_NAME = "ldtteam." + BlockUI.MOD_ID + ".cursor";
    }
}
