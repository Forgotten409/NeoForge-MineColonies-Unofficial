package com.minecolonies.core.client.gui.containers;

import com.minecolonies.api.inventory.container.ContainerGrave;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

public class WindowGrave extends AbstractContainerScreen<ContainerGrave>
{
    /**
     * The resource LOCATION of the texture.
     */
    private static final Identifier CHEST_GUI_TEXTURE = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/generic_108.png");

    /**
     * The LOCATION of the additional styles.
     */
    private static final String LOCATION = "textures/gui/gui%s.png";

    /**
     * Amount of slots each row.
     */
    private static final int SLOTS_EACH_ROW = 9;

    /**
     * Size of the custom texture.
     */
    private static final int TEXTURE_SIZE = 350;

    /**
     * Offset of each slot.
     */
    private static final int SLOT_OFFSET = 18;

    /**
     * Size at which the normal GUI texture still works.
     */
    private static final int GOOD_SIZE = 8;

    /**
     * Multiply the current size by this amount.
     */
    private static final int SIZE_MULTIPLIER = 3;

    /**
     * General y offset.
     */
    private static final int Y_OFFSET = 114;

    /**
     * Offet of the screen for the texture.
     */
    private static final int TEXTURE_HEIGHT = 96;

    /**
     * Offset inside the texture to use.
     */
    private static final int TEXTURE_OFFSET = 126 * 2 - 17;

    /**
     * Extra offset to move increase the texture if the inventory is huge.
     */
    private static final int EXTRA_OFFSET = 56;

    /**
     * Extra height to show the whole texture for big inventories.
     */
    private static final int EXTRA_HEIGHT = 50;

    /**
     * The upper chest inventory.
     */
    private final IItemHandler inv;

    /**
     * Used to calculate the window height.
     */
    private final int inventoryRows;

    public WindowGrave(final ContainerGrave container, final Inventory playerInventory, final Component iTextComponent)
    {
        // PORT26: imageWidth/imageHeight are final — the old computed assignments moved into
        // the 5-arg constructor (176 was the 1.21.1 default width).
        super(container,
            playerInventory,
            iTextComponent,
            graveWidth(container.grave.getInventory().getSlots() / SLOTS_EACH_ROW),
            graveHeight(container.grave.getInventory().getSlots() / SLOTS_EACH_ROW));
        this.inv = container.grave.getInventory();

        this.inventoryRows = inv.getSlots() / SLOTS_EACH_ROW;
    }

    private static int graveHeight(final int inventoryRows)
    {
        return Y_OFFSET + Math.min(SLOTS_EACH_ROW, inventoryRows) * SLOT_OFFSET;
    }

    private static int graveWidth(final int inventoryRows)
    {
        return inventoryRows > SLOTS_EACH_ROW - 1
                 ? 176 + (inventoryRows - SLOTS_EACH_ROW) * (SLOTS_EACH_ROW + 1)
                 : 176;
    }

    /**
     * Draw the foreground layer for the GuiContainer (everything in front of the items)
     */
    @Override
    protected void extractLabels(@NotNull final GuiGraphicsExtractor stack, int mouseX, int mouseY)
    {
        stack.text(this.font, this.title.getString(), 8, 6, 0xFF404040, false);
        stack.text(this.font, this.playerInventoryTitle.getString(), 8, (this.imageHeight - (inventoryRows > 6 ? 110 : 94)), 0xFF404040, false);
    }

    /**
     * Draws the background layer of this container (behind the items).
     */
    @Override
    public void extractBackground(@NotNull final GuiGraphicsExtractor stack, final int mouseX, final int mouseY, final float partialTicks)
    {
        // PORT26: Screen#extractBackground draws the dark overlay behind the GUI — call it
        // first like every vanilla container screen does.
        super.extractBackground(stack, mouseX, mouseY, partialTicks);
        final Identifier loc = getCorrectTextureForSlots(inventoryRows);

        final int i = (this.width - this.imageWidth) / 2;
        final int j = (this.height - this.imageHeight) / 2;

        if (inventoryRows < SLOTS_EACH_ROW)
        {
            // PORT26-fix: drop the stray trailing TEXTURE_SIZE — with 11 args this resolved
            // to blit(…,textureWidth,textureHeight,color) with color=350 (alpha 0) → invisible.
            stack.blit(RenderPipelines.GUI_TEXTURED, loc, i, j, 0, 0, this.imageWidth, this.inventoryRows * SLOT_OFFSET + SLOT_OFFSET - 1, TEXTURE_SIZE, TEXTURE_SIZE);
            stack.blit(RenderPipelines.GUI_TEXTURED, loc, i, j + this.inventoryRows * SLOT_OFFSET + SLOT_OFFSET - 1, 0, TEXTURE_OFFSET, this.imageWidth, TEXTURE_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
        }
        else
        {
            final int textureOffset = TEXTURE_OFFSET - EXTRA_OFFSET;
            stack.blit(RenderPipelines.GUI_TEXTURED, loc, i, j, 0, 0, (this.imageWidth * SIZE_MULTIPLIER) / 2, this.inventoryRows * SLOT_OFFSET + SLOT_OFFSET - 1, TEXTURE_SIZE, TEXTURE_SIZE);
            stack.blit(RenderPipelines.GUI_TEXTURED, loc, i, j + Math.min(SLOTS_EACH_ROW, this.inventoryRows) * SLOT_OFFSET + SLOT_OFFSET - 1, 0, textureOffset, (this.imageWidth * SIZE_MULTIPLIER) / 2, TEXTURE_HEIGHT + EXTRA_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
        }
    }

    /**
     * Get the correct resource LOCATION for this amount of rows.
     *
     * @param inventoryRows the amount of rows.
     * @return the correct LOCATION.
     */
    private static Identifier getCorrectTextureForSlots(final int inventoryRows)
    {
        if (inventoryRows <= GOOD_SIZE)
        {
            return CHEST_GUI_TEXTURE;
        }
        else
        {
            return Identifier.fromNamespaceAndPath(Constants.MOD_ID, String.format(LOCATION, Integer.toString(inventoryRows * SLOTS_EACH_ROW)));
        }
    }

    // PORT26: the old render()+renderTooltip() override was dropped — the new
    // extractRenderState flow of AbstractContainerScreen submits both contents and tooltips.
}
