package com.minecolonies.core.client.gui.containers;

import com.minecolonies.api.inventory.container.ContainerBuildingInventory;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class WindowBuildingInventory extends AbstractContainerScreen<ContainerBuildingInventory>
{
    /**
     * Texture res loc.
     */
    private static final Identifier TEXT = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/generic_108.png");

    /**
     * Offset inside the texture to use.
     */
    private static final int TEXTURE_OFFSET = 126 * 2 - 17;

    /**
     * Offset of each slot.
     */
    private static final int SLOT_OFFSET = 18;

    /**
     * Size of the custom texture.
     */
    private static final int TEXTURE_SIZE = 350;

    /**
     * Offet of the screen for the texture.
     */
    private static final int TEXTURE_HEIGHT = 96;

    /**
     * In rows total.
     */
    private final int inventoryRows;

    public WindowBuildingInventory(final ContainerBuildingInventory container, final Inventory playerInventory, final Component component)
    {
        // PORT26: imageWidth/imageHeight are final — passed through the 5-arg constructor
        // (176 is the 1.21.1 default width; height is the old computed assignment).
        super(container, playerInventory, component, 176, 114 + container.getSize() * 18);
        this.inventoryRows = container.getSize();
    }

    // PORT26: the old render()+renderTooltip() override was dropped — the new
    // extractRenderState flow of AbstractContainerScreen submits both contents and tooltips.

    /**
     * Draw the foreground layer for the GuiContainer (everything in front of the items)
     */
    @Override
    protected void extractLabels(@NotNull final GuiGraphicsExtractor stack, int mouseX, int mouseY)
    {
        stack.text(this.font, this.title.getString(), 8, 6, 0xFF404040, false);
        stack.text(this.font, this.playerInventoryTitle.getString(), 8, (this.imageHeight - 96 + 2), 0xFF404040, false);
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
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        // PORT26-fix: drop the stray trailing TEXTURE_SIZE — with 11 args this resolved
        // to blit(…,textureWidth,textureHeight,color) with color=350 (alpha 0) → invisible.
        stack.blit(RenderPipelines.GUI_TEXTURED, TEXT, i, j, 0, 0, this.imageWidth, this.inventoryRows * SLOT_OFFSET + SLOT_OFFSET - 1, TEXTURE_SIZE, TEXTURE_SIZE);
        stack.blit(RenderPipelines.GUI_TEXTURED, TEXT, i, j + this.inventoryRows * SLOT_OFFSET + SLOT_OFFSET - 1, 0, TEXTURE_OFFSET, this.imageWidth, TEXTURE_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
    }
}
