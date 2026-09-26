package com.ldtteam.blockui;

import com.ldtteam.blockui.mod.item.BlockStateRenderingData;
import com.ldtteam.blockui.util.cursor.Cursor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

/**
 * BlockUI render target — PORT 26.1.2.
 *
 * <p>Originally (1.21.1) this class extended {@code GuiGraphics} directly, using an AT-opened
 * constructor {@code (Minecraft, PoseStack, BufferSource)}. In 26.1.2 {@code GuiGraphics} no
 * longer exists — GUI rendering goes through {@link GuiGraphicsExtractor}, whose constructor
 * is private (instances are created by the vanilla GUI pipeline per frame).
 *
 * <p>Port strategy: <b>composition</b>. This class wraps the frame-provided
 * {@link GuiGraphicsExtractor} and keeps the legacy BlockUI method names
 * ({@code drawString}, {@code renderItem}, {@code renderItemDecorations}, {@code pose()}) so
 * the Pane/controls/views tree needs only minimal signature changes. Legacy methods map onto
 * the new extract-style API:
 * <ul>
 *   <li>{@code drawString(...)} → {@code text(...)} (plus manual width tracking for the
 *       returned pen position, since {@code text} returns {@code void})</li>
 *   <li>{@code renderItem(...)} → {@code item(...)}</li>
 *   <li>{@code renderItemDecorations(...)} → {@code itemDecorations(...)}</li>
 *   <li>{@code pose()} returns the new {@link Matrix3x2fStack} (2D transforms)</li>
 * </ul>
 */
public class BOGuiGraphics
{
    private final Minecraft minecraft;
    private final GuiGraphicsExtractor gui;

    // Cursor selection: original code compared PoseStack depth (deeper pane wins).
    // Matrix3x2fStack does not expose depth, so we use "reset at frame start + last set wins".
    // Children draw after parents in the Pane tree, so the deepest pane generally sets last.
    // PORT26 limitation note: no depth accessor on the 2D pose stack — the last-wins
    // heuristic below is the closest approximation.
    private Cursor selectedCursor = Cursor.DEFAULT;

    /**
     * Current blit tint (ARGB) applied by the blit primitives; {@code 0xFFFFFFFF} means
     * untinted (fast path, no color argument passed to vanilla).
     */
    private int blitTint = UNTINTED;

    /** untinted marker — white, fully opaque */
    private static final int UNTINTED = 0xFFFFFFFF;

    public BOGuiGraphics(final Minecraft minecraft, final GuiGraphicsExtractor gui)
    {
        this.minecraft = minecraft;
        this.gui = gui;
    }

    public GuiGraphicsExtractor gui()
    {
        return gui;
    }

    public Minecraft minecraft()
    {
        return minecraft;
    }

    public Font font()
    {
        return minecraft.font;
    }

    public Matrix3x2fStack pose()
    {
        return gui.pose();
    }

    // ---------- text ----------

    /**
     * PORT26: {@code GuiGraphicsExtractor#text} silently skips text whose ARGB alpha byte
     * is zero. Legacy BlockUI colors (including the {@link Color} name map and XML
     * {@code color}/{@code textcolor} attributes) are 24-bit RGB without an alpha byte,
     * which made every string invisible after the port. Treat a missing/negligible alpha
     * (&lt; 4, matching vanilla's own legacy-color heuristic) as fully opaque.
     */
    public static int withDefaultTextAlpha(final int color)
    {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }

    public int drawString(final String text, final float x, final float y, final int color)
    {
        return drawString(text, x, y, color, false);
    }

    /**
     * @return pen x position after the drawn text (legacy drawString contract).
     */
    public int drawString(final String text, final float x, final float y, final int color, final boolean shadow)
    {
        gui.text(minecraft.font, text, (int) x, (int) y, withDefaultTextAlpha(color), shadow);
        return (int) x + minecraft.font.width(text);
    }

    // ---------- items ----------

    private Font getFont(@Nullable final ItemStack itemStack)
    {
        if (itemStack != null)
        {
            final Font font = IClientItemExtensions.of(itemStack).getFont(itemStack, IClientItemExtensions.FontContext.ITEM_COUNT);
            if (font != null)
            {
                return font;
            }
        }
        return minecraft.font;
    }

    public void renderItem(final ItemStack itemStack, final int x, final int y)
    {
        gui.item(itemStack, x, y);
    }

    public void renderItemDecorations(final ItemStack itemStack, final int x, final int y)
    {
        gui.itemDecorations(getFont(itemStack), itemStack, x, y);
    }

    public void renderItemDecorations(final ItemStack itemStack, final int x, final int y, @Nullable final String altStackSize)
    {
        gui.itemDecorations(getFont(itemStack), itemStack, x, y, altStackSize);
    }

    /**
     * Render given blockState with model just like {@link #renderItem(ItemStack, int, int)}.
     *
     * <p>PORT26: the 1.21.1 implementation rendered the block state (and its block entity
     * + fluids) directly through the block renderer into the GUI buffer, relying on
     * {@code ModelBakery} ATs and the old immediate-mode buffer source. The 26.1.2 GUI
     * pipeline has no direct buffer access, so this renders through the item path
     * instead: the vanilla item model resolver honors the stack's BLOCK_STATE component,
     * which covers block-state property overrides. Block-entity-driven retexturing (e.g.
     * domum-ornamentum) needs the fake-level block rendering path and is deferred until
     * that content phase is ported — see PORTING.md known limitations.
     */
    public void renderBlockStateAsItem(final BlockStateRenderingData data, final ItemStack itemStack)
    {
        gui.item(itemStack, 0, 0);
    }

    // ---------- primitives (fill / gradient / blit / scissor) ----------

    public void fill(final int x0, final int y0, final int x1, final int y1, final int color)
    {
        gui.fill(x0, y0, x1, y1, color);
    }

    public void fillGradient(final int x0, final int y0, final int x1, final int y1, final int colorFrom, final int colorTo)
    {
        gui.fillGradient(x0, y0, x1, y1, colorFrom, colorTo);
    }

    /**
     * Runs the given block with a blit tint active — the 26.1.2 replacement for the old
     * global {@code RenderSystem.setShaderColor} dimming (per-draw color instead of
     * global state). Textures blitted through the standard blit primitives inside the
     * block are multiplied with the tint; raw-UV blits are not tintable (vanilla has no
     * color variant of that overload — used for repeatable/nine-slice paths where widget
     * tinting does not apply).
     *
     * @param tint  ARGB tint, {@code 0xFFFFFFFF} for no tint
     * @param block rendering to run under the tint
     */
    public void withBlitTint(final int tint, final Runnable block)
    {
        final int oldTint = blitTint;
        blitTint = tint;
        try
        {
            block.run();
        }
        finally
        {
            blitTint = oldTint;
        }
    }

    public void blit(final Identifier texture, final int x, final int y, final float u, final float v, final int width, final int height, final int textureWidth, final int textureHeight)
    {
        if (blitTint != UNTINTED)
        {
            gui.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight, blitTint);
        }
        else
        {
            gui.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        }
    }

    public void blit(final Identifier texture, final int x, final int y, final float u, final float v, final int width, final int height, final int srcWidth, final int srcHeight, final int textureWidth, final int textureHeight)
    {
        if (blitTint != UNTINTED)
        {
            gui.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, srcWidth, srcHeight, textureWidth, textureHeight, blitTint);
        }
        else
        {
            gui.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, srcWidth, srcHeight, textureWidth, textureHeight);
        }
    }

    /**
     * Raw UV blit — direct replacement for the old custom POSITION_TEX tessellation.
     */
    public void blit(final Identifier texture, final int x0, final int y0, final int x1, final int y1, final float u0, final float u1, final float v0, final float v1)
    {
        gui.blit(texture, x0, y0, x1, y1, u0, u1, v0, v1);
    }

    public void enableScissor(final int x0, final int y0, final int x1, final int y1)
    {
        gui.enableScissor(x0, y0, x1, y1);
    }

    public void disableScissor()
    {
        gui.disableScissor();
    }

    // ---------- cursor ----------

    public void setCursor(final Cursor cursor)
    {
        selectedCursor = cursor;
    }

    /**
     * Reset cursor selection at the start of a frame.
     */
    public void resetCursorFrame()
    {
        selectedCursor = Cursor.DEFAULT;
    }

    /**
     * @param debugXoffset debug string x offset
     */
    public void applyCursor(final int debugXoffset)
    {
        selectedCursor.apply();

        if (Pane.debugging)
        {
            drawString(selectedCursor.toString(), debugXoffset, -minecraft.font.lineHeight, Color.getByName("white"));
        }
    }

    public static double getAltSpeedFactor()
    {
        return net.minecraft.client.Minecraft.getInstance().hasAltDown() ? 5 : 1;
    }
}
