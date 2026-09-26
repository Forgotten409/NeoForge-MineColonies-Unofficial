package com.ldtteam.structurize.client;

import com.ldtteam.structurize.items.ItemStackTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;

/**
 * PORT26: the tooltip component contract changed in 26.1.2 —
 * <ul>
 *   <li>{@code getHeight()} → {@code getHeight(Font)};</li>
 *   <li>{@code renderText(Font, x, y, Matrix4f, BufferSource)} →
 *       {@code extractText(GuiGraphicsExtractor, Font, x, y)} (GUI rendering no longer goes
 *       through PoseStack/MultiBufferSource — the extractor records the draw command; the old
 *       background color / display mode parameters have no equivalent);</li>
 *   <li>{@code renderImage(Font, x, y, GuiGraphics)} →
 *       {@code extractImage(Font, x, y, w, h, GuiGraphicsExtractor)}, with
 *       {@code GuiGraphicsExtractor#item} / {@code itemDecorations} replacing
 *       {@code GuiGraphics#renderItem} / {@code renderItemDecorations}.</li>
 * </ul>
 */
public class ClientItemStackTooltip implements ClientTooltipComponent
{
    private final ItemStackTooltip component;

    public ClientItemStackTooltip(@NotNull final ItemStackTooltip component)
    {
        this.component = component;
    }

    @Override
    public int getHeight(@NotNull final Font font)
    {
        return 20;
    }

    @Override
    public int getWidth(@NotNull Font font)
    {
        return 20 + font.width(this.component.getStack().getDisplayName().getVisualOrderText());
    }

    @Override
    public void extractText(@NotNull final GuiGraphicsExtractor extractor, @NotNull final Font font, final int x, final int y)
    {
        // PORT26: font.drawInBatch(..., pose, buffers, DisplayMode, backgroundColor, color) →
        // extractor.text(...) — position/pose is managed by the tooltip renderer itself.
        // (int coords now; the old /2f rounding difference is sub-pixel.)
        extractor.text(font, this.component.getStack().getHoverName(), x + 20, y + (20 - font.lineHeight) / 2, 0xffffffff);
    }

    @Override
    public void extractImage(final Font font, final int x, final int y, final int width, final int height, @NotNull final GuiGraphicsExtractor extractor)
    {
        extractor.item(this.component.getStack(), x + 2, y + 2);
        extractor.itemDecorations(getFont(this.component.getStack()), this.component.getStack(), x + 2, y + 2);
    }

    /**
     * @see com.ldtteam.blockui.BOGuiGraphics#getFont
     */
    private Font getFont(final ItemStack itemStack)
    {
        if (itemStack != null)
        {
            final Font font = IClientItemExtensions.of(itemStack).getFont(itemStack, IClientItemExtensions.FontContext.ITEM_COUNT);
            if (font != null)
            {
                return font;
            }
        }
        return Minecraft.getInstance().font;
    }
}
