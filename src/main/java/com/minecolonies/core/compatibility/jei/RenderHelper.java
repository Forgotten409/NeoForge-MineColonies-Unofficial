package com.minecolonies.core.compatibility.jei;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * JEI GUI rendering utility helpers
 */
public class RenderHelper
{
    /**
     * Render a block model on a GUI.
     *
     * <p>PORT26: the 1.21.1 implementation used the immediate-mode block renderer
     * ({@code BlockRenderDispatcher#renderSingleBlock} through {@code Minecraft#getBlockRenderer()},
     * writing into the GUI buffer source and flushing it). In 26.1.2 that entire path is gone —
     * block rendering goes through render-state extraction and the GUI pipeline exposes no
     * buffer source. Following the same fallback the BlockUI port established
     * ({@code BOGuiGraphics#renderBlockStateAsItem}), the block is rendered through the item
     * path: a block's item model IS its block model, drawn by the vanilla item renderer with
     * the standard isometric block transform. The old call sites anchor the preview at its
     * bottom-center with a ~16px footprint, so the 16x16 item icon is drawn at
     * {@code (x - 8, y - 16)} to approximate that footprint; pitch/yaw/z are ignored (the item
     * renderer applies its own block transform).
     *
     * @param ctx   the GUI graphics
     * @param block the blockstate to render
     * @param x     horizontal center position
     * @param y     vertical bottom position
     * @param z     distance from camera (unused — see note above)
     * @param pitch rotation forwards (unused — see note above)
     * @param yaw   rotation sideways (unused — see note above)
     * @param scale scaling factor (unused — item icons are 16x16)
     */
    public static void renderBlock(final GuiGraphicsExtractor ctx, final BlockState block, final float x, final float y, final float z, final float pitch, final float yaw, final float scale)
    {
        ctx.item(new ItemStack(block.getBlock()), Math.round(x - 8), Math.round(y - 16));
    }
}
