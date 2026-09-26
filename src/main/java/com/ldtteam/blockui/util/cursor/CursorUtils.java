package com.ldtteam.blockui.util.cursor;

import com.ldtteam.blockui.util.texture.CursorTexture;
import com.ldtteam.blockui.util.texture.MissingCursorTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper class to manage cursor image.
 */
public class CursorUtils
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CursorUtils.class);
    private static final long[] STANDARD_CURSORS = new long[StandardCursor.values().length];
    private static long lastCursorAddress = 0;

    /**
     * Cursor textures we registered: identifier -> instance (failed loads map to the
     * fallback). The 26.1.2 TextureManager has no peek-only accessor (getTexture
     * auto-registers a SimpleTexture on miss, which logs errors for missing files and
     * breaks on ./-style out-of-jar paths), so we track our own registrations.
     */
    private static final Map<Identifier, CursorTexture> CURSOR_TEXTURES = new ConcurrentHashMap<>();

    /**
     * Sets cursor image using given resource location.
     * Do not forget to load given resLoc as CursorTexture first
     *
     * @param  rl image resource location
     * @return    cursor texture reference
     * @see #loadCursorTexture(Identifier)
     */
    public static CursorTexture setCursorImage(final Identifier rl)
    {
        final AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(rl);
        final CursorTexture cursorTexture;
        if (texture instanceof final CursorTexture loaded)
        {
            cursorTexture = loaded;
        }
        else
        {
            // PORT26: 1.21.1 detected the missing-texture singleton here (failed loads were
            // replaced by the manager) and swapped in the missing-cursor fallback; 26.1.2
            // keeps a broken plain texture registered instead, so anything that is not a
            // CursorTexture takes the same fallback.
            LOGGER.warn("Cursor texture is not loaded as CursorTexture (missing file or forgot to load?), using fallback: {}", rl);
            cursorTexture = MissingCursorTexture.INSTANCE;
        }

        cursorTexture.setCursor();
        return cursorTexture;
    }

    /**
     * Makes sure client global texture manager has CursorTexture assigned to given resLoc
     *
     * <p>PORT26: re-loads on every call. CursorTexture is a plain AbstractTexture and as
     * such is outside the 26.1.2 ReloadableTexture reload system — re-loading here (once
     * per pane construction / GUI open, cheap) doubles as the resource-reload refresh.
     *
     * @param resLoc cursor file location
     */
    public static void loadCursorTexture(final Identifier resLoc)
    {
        final TextureManager texManager = Minecraft.getInstance().getTextureManager();

        CursorTexture cursorTexture = CURSOR_TEXTURES.get(resLoc);
        if (cursorTexture == null || cursorTexture == MissingCursorTexture.INSTANCE)
        {
            // not loaded yet, or the previous load failed — retry (the file may exist
            // after a resource reload)
            cursorTexture = new CursorTexture(resLoc);
        }

        try
        {
            // re-load on every call: cheap (once per pane construction / GUI open) and it
            // doubles as the resource-reload refresh — cursors are plain
            // AbstractTextures, outside the 26.1.2 ReloadableTexture reload system
            cursorTexture.load(Minecraft.getInstance().getResourceManager());
            texManager.register(resLoc, cursorTexture);
            CURSOR_TEXTURES.put(resLoc, cursorTexture);
        }
        catch (final IOException e)
        {
            // 1.21.1 behavior: the manager replaced failed cursor loads with the missing
            // texture singleton — mirror that with the missing-cursor fallback
            texManager.register(resLoc, MissingCursorTexture.INSTANCE);
            CURSOR_TEXTURES.put(resLoc, MissingCursorTexture.INSTANCE);
            LOGGER.warn("Failed to load cursor texture, using fallback: {}", resLoc);
        }
    }

    /**
     * Sets cursor image to standard shapes provided by GLFW.
     * 
     * @param shape cursor shape
     */
    public static void setStandardCursor(final StandardCursor shape)
    {
        if (shape == StandardCursor.DEFAULT)
        {
            resetCursor();
            return;
        }

        final int ord = shape.ordinal();

        if (STANDARD_CURSORS[ord] == 0)
        {
            RenderSystem.assertOnRenderThread();
            STANDARD_CURSORS[ord] = GLFW.glfwCreateStandardCursor(shape.glfwValue);
            if (STANDARD_CURSORS[ord] == 0)
            {
                LOGGER.error("Cannot create standard cursor for shape: " + shape);
                return;
            }
        }

        setCursorAddress(STANDARD_CURSORS[ord]);
    }

    /**
     * Sets cursor image to default (usually arrow).
     */
    public static void resetCursor()
    {
        setCursorAddress(0);
    }

    /**
     * Sets cursor image address. If null (zero), cursor is reset to default (usually arrow).
     * 
     * @param cursorAddress cursor handle address or null
     */
    public static void setCursorAddress(final long cursorAddress)
    {
        RenderSystem.assertOnRenderThread();
        if (cursorAddress != lastCursorAddress)
        {
            GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().handle(), cursorAddress);
            lastCursorAddress = cursorAddress;
        }
    }

    /**
     * @param  testedAddress param of tested cursor handle
     * @return               true if given address is equal to current cursor handle address
     */
    public static boolean isCurrentCursor(final long testedAddress)
    {
        return testedAddress == lastCursorAddress;
    }

    /**
     * Enum represing all possible cursors defined by GLFW
     */
    public enum StandardCursor
    {
        DEFAULT(Integer.MIN_VALUE),
        ARROW(GLFW.GLFW_ARROW_CURSOR),
        TEXT_CURSOR(GLFW.GLFW_IBEAM_CURSOR),
        CROSSHAIR(GLFW.GLFW_CROSSHAIR_CURSOR),
        HAND(GLFW.GLFW_POINTING_HAND_CURSOR),
        HORIZONTAL_RESIZE(GLFW.GLFW_RESIZE_EW_CURSOR),
        VERTICAL_RESIZE(GLFW.GLFW_RESIZE_NS_CURSOR),
        RESIZE(GLFW.GLFW_RESIZE_ALL_CURSOR),

        /** unsafe */
        RESIZE2(GLFW.GLFW_RESIZE_NWSE_CURSOR),
        /** unsafe */
        RESIZE3(GLFW.GLFW_RESIZE_NESW_CURSOR);

        private final int glfwValue;

        private StandardCursor(final int glfwValue)
        {
            this.glfwValue = glfwValue;
        }
    }
}
