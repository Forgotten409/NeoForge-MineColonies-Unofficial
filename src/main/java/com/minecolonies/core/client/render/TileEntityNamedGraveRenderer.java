package com.minecolonies.core.client.render;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.blocks.huts.AbstractBlockMinecoloniesDefault;
import com.minecolonies.core.tileentities.TileEntityNamedGrave;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Direction;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Style;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * PORT26: render-state rewrite — the grave text lines and facing are extracted into
 * {@link State}; the text is submitted through the submit collector's text path
 * (was Font#drawInBatch with a MultiBufferSource).
 */
public class TileEntityNamedGraveRenderer implements BlockEntityRenderer<TileEntityNamedGrave, TileEntityNamedGraveRenderer.State>
{
    /**
     * Basic rotation to achieve a certain direction.
     */
    private static final int BASIC_ROTATION = 90;

    /**
     * Rotate by amount to go east.
     */
    private static final int ROTATE_EAST = 1;

    /**
     * Rotate by amount to go north.
     */
    private static final int ROTATE_NORTH = 2;

    /**
     * Rotate by amount to go west.
     */
    private static final int ROTATE_WEST = 3;

    /**
     * PORT26: render state of the named grave.
     */
    public static class State extends BlockEntityRenderState
    {
        public boolean namedGraveBlock;
        @Nullable
        public Direction facing;
        public List<String> textLines = new ArrayList<>();
    }

    /**
     * PORT26: font for measuring the centered text lines.
     */
    private final Font font;

    public TileEntityNamedGraveRenderer(final BlockEntityRendererProvider.Context context)
    {
        this.font = context.font();
    }

    @Override
    public TileEntityNamedGraveRenderer.State createRenderState()
    {
        return new TileEntityNamedGraveRenderer.State();
    }

    @Override
    public void extractRenderState(
      @NotNull final TileEntityNamedGrave tileEntity,
      @NotNull final TileEntityNamedGraveRenderer.State state,
      final float partialTicks,
      final Vec3 cameraPosition,
      @Nullable final ModelFeatureRenderer.CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tileEntity, state, partialTicks, cameraPosition, breakProgress);

        state.namedGraveBlock = tileEntity.getLevel() != null
                                   && tileEntity.getLevel().getBlockState(tileEntity.getBlockPos()).getBlock() == ModBlocks.blockNamedGrave;
        if (state.namedGraveBlock)
        {
            state.facing = tileEntity.getLevel().getBlockState(tileEntity.getBlockPos()).getValue(AbstractBlockMinecoloniesDefault.FACING);
        }
        else
        {
            state.facing = null;
        }
        state.textLines = new ArrayList<>(tileEntity.getTextLines());
    }

    @Override
    public void submit(
      @NotNull final TileEntityNamedGraveRenderer.State state,
      @NotNull final PoseStack matrixStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        matrixStack.pushPose();

        if (state.namedGraveBlock && state.facing != null)
        {
            switch (state.facing)
            {
                case NORTH:
                    matrixStack.translate(0.5f, 1.18F, 0.48F); //in front of the center point of the name plate
                    matrixStack.scale(0.006F, -0.006F, 0.006F); //size of the text font
                    matrixStack.mulPose(Axis.YP.rotationDegrees(BASIC_ROTATION * ROTATE_NORTH));
                    break;
                case SOUTH:
                    matrixStack.translate(0.5f, 1.18F, 0.54F); //in front of the center point of the name plate
                    matrixStack.scale(0.006F, -0.006F, 0.006F); //size of the text font
                    //don't rotate at all.
                    break;

                case EAST:
                    matrixStack.translate(0.54f, 1.18F, 0.5F); //in front of the center point of the name plate
                    matrixStack.scale(0.006F, -0.006F, 0.006F); //size of the text font
                    matrixStack.mulPose(Axis.YP.rotationDegrees(BASIC_ROTATION * ROTATE_EAST));
                    break;
                case WEST:
                    matrixStack.translate(0.48f, 1.18F, 0.5F); //in front of the center point of the name plate
                    matrixStack.scale(0.006F, -0.006F, 0.006F); //size of the text font
                    matrixStack.mulPose(Axis.YP.rotationDegrees(BASIC_ROTATION * ROTATE_WEST));
                    break;
            }

            if (state.textLines.isEmpty())
            {
                renderText(matrixStack, submitNodeCollector, state, "Unknown Citizen", 0);
            }
            else
            {
                for (int i = 0; i < state.textLines.size(); i++)
                {
                    renderText(matrixStack, submitNodeCollector, state, state.textLines.get(i), i);
                }
            }
        }

        // restore the original transformation matrix + normals matrix
        matrixStack.popPose();
    }

    private void renderText(final PoseStack matrixStack, final SubmitNodeCollector submitNodeCollector, final TileEntityNamedGraveRenderer.State state, String text, final int line)
    {
        final int maxSize = 20;
        if (text.length() > maxSize)
        {
            text = text.substring(0, maxSize);
        }

        final FormattedCharSequence iReorderingProcessor = FormattedCharSequence.forward(text, Style.EMPTY);
        if (iReorderingProcessor != null)
        {
            // render width of text divided by 2 — keeps the centered layout of the 1.21.1 renderer
            final float x = (float) (-this.font.width(iReorderingProcessor) / 2);
            // PORT26: text submitted through the collector (was Font#drawInBatch).
            submitNodeCollector.submitText(
              matrixStack,
              x,
              line * 10f,
              iReorderingProcessor,
              false,
              Font.DisplayMode.NORMAL,
              state.lightCoords,
              0xdcdcdc00,
              0,
              0);
        }
    }
}
