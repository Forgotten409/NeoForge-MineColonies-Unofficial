package com.minecolonies.core.client.render;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.core.client.render.worldevent.WorldEventContext;
import com.minecolonies.core.tileentities.TileEntityColonySign;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Style;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.SubmitNodeStorageExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.minecolonies.api.util.constant.TranslationConstants.NEXT;
import static com.minecolonies.api.util.constant.TranslationConstants.PREVIOUS;
import static com.minecolonies.core.blocks.BlockColonySign.CONNECTED;

/**
 * Renders the colony navigation signs.
 *
 * <p>PORT26: render-state rewrite — the sign data (rotation, colony names/distances,
 * connected state) is extracted into {@link State}; the sign block model is submitted
 * through the NeoForge multi-layer block model submit (the old BakedModel +
 * ModelBlockRenderer#renderModel path is gone) and the text is submitted through the
 * collector's text path (was Font#drawInBatch).</p>
 */
public class TileEntityColonySignRenderer implements BlockEntityRenderer<TileEntityColonySign, TileEntityColonySignRenderer.State>
{
    /**
     * PORT26: render state of the colony sign — everything the submit pass needs.
     */
    public static class State extends BlockEntityRenderState
    {
        public boolean colonySignBlock;
        public boolean connected;
        public float relativeRotationToColony;
        public String colonyName = "";
        public int colonyDistance;
        public String targetColonyName = "";
        public int targetColonyDistance;
        public int targetColonyId;
        public int cachedSignAboveColony;
    }

    /**
     * Font for measuring the centered text.
     */
    private final Font font;

    public TileEntityColonySignRenderer(final BlockEntityRendererProvider.Context context)
    {
        this.font = context.font();
    }

    @Override
    public TileEntityColonySignRenderer.State createRenderState()
    {
        return new TileEntityColonySignRenderer.State();
    }

    @Override
    public void extractRenderState(
      @NotNull final TileEntityColonySign tileEntity,
      @NotNull final TileEntityColonySignRenderer.State state,
      final float partialTicks,
      final Vec3 cameraPosition,
      @Nullable final ModelFeatureRenderer.CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tileEntity, state, partialTicks, cameraPosition, breakProgress);

        final BlockState blockState = tileEntity.getBlockState();
        state.colonySignBlock = blockState.getBlock() == ModBlocks.blockColonySign;
        state.connected = state.colonySignBlock && tileEntity.getTargetColonyId() != tileEntity.getCachedSignAboveColony();
        state.relativeRotationToColony = tileEntity.getRelativeRotation();
        state.colonyName = tileEntity.getColonyName();
        state.colonyDistance = tileEntity.getColonyDistance();
        state.targetColonyName = tileEntity.getTargetColonyName();
        state.targetColonyDistance = tileEntity.getTargetColonyDistance();
        state.targetColonyId = tileEntity.getTargetColonyId();
        state.cachedSignAboveColony = tileEntity.getCachedSignAboveColony();
    }

    @Override
    public void submit(
      @NotNull final TileEntityColonySignRenderer.State state,
      @NotNull final PoseStack matrixStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        if (!state.colonySignBlock)
        {
            return;
        }

        matrixStack.pushPose();
        matrixStack.translate(0.5, 0.5, 0.5);
        matrixStack.mulPose(Axis.YP.rotationDegrees(state.relativeRotationToColony));
        matrixStack.translate(-0.5, -0.5, -0.5);
        submitSingleBlock(state, matrixStack, submitNodeCollector, state.connected);
        matrixStack.popPose();

        this.submitTextOnSide(matrixStack, submitNodeCollector, state, true);
        this.submitTextOnSide(matrixStack, submitNodeCollector, state, false);
    }

    /**
     * Submits the sign block model (the sign with or without the "connected" marker).
     *
     * @param state the render state.
     * @param pose the pose stack.
     * @param submitNodeCollector the submit collector.
     * @param connected if two colonies are connected.
     */
    private static void submitSingleBlock(
      final TileEntityColonySignRenderer.State state,
      final PoseStack pose,
      final SubmitNodeCollector submitNodeCollector,
      final boolean connected)
    {
        // PORT26: BakedModel#getRenderTypes + ModelBlockRenderer#renderModel are gone — the
        // sign model is submitted through the multi-layer block model submit (structurize
        // tag anchor pattern), resolving the connected/unconnected block state variant.
        final Minecraft mc = Minecraft.getInstance();
        final BlockState blockState = connected
                                         ? ModBlocks.blockColonySign.defaultBlockState().setValue(CONNECTED, true)
                                         : ModBlocks.blockColonySign.defaultBlockState();
        final BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(blockState);
        final List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(), parts);
        if (!parts.isEmpty() && submitNodeCollector instanceof final SubmitNodeStorageExtension extension)
        {
            extension.submitMultiLayerBlockModel(
              pose,
              parts,
              model.hasMaterialFlag(BakedQuad.FLAG_TRANSLUCENT),
              new int[0],
              state.lightCoords,
              OverlayTexture.NO_OVERLAY,
              0);
        }
    }

    /**
     * Submits the text on the sign and handle rotation etc.
     * @param matrixStack the matrix stack.
     * @param submitNodeCollector the submit collector.
     * @param state the render state.
     * @param mirrored if mirrored or not.
     */
    private void submitTextOnSide(
      final PoseStack matrixStack,
      final SubmitNodeCollector submitNodeCollector,
      final TileEntityColonySignRenderer.State state,
      final boolean mirrored)
    {
        matrixStack.pushPose();
        matrixStack.translate(0.5f, 0.5F, 0.5f);
        matrixStack.mulPose(Axis.YP.rotationDegrees(state.relativeRotationToColony));
        if (mirrored)
        {
            matrixStack.mulPose(Axis.YP.rotationDegrees(180));
        }
        matrixStack.translate(-0.0f, -0.1F, 0.2f);

        matrixStack.scale(0.007F, -0.007F, 0.007F);

        if (state.colonyName.isEmpty())
        {
            this.renderText(matrixStack, submitNodeCollector, state, "Unknown Colony", 0, 0);
            this.renderText(matrixStack, submitNodeCollector, state,
              Component.translatable("com.minecolonies.coremod.dist.blocks", state.colonyDistance).getString(), 3, 0);
        }
        else
        {
            final String targetColonyName = state.targetColonyName;
            if (!targetColonyName.isEmpty() && state.targetColonyId != state.cachedSignAboveColony)
            {
                this.renderColonyNameOnSign(state.colonyName, matrixStack, submitNodeCollector, state, state.colonyDistance, -10);
                this.renderColonyNameOnSign(targetColonyName, matrixStack, submitNodeCollector, state, state.targetColonyDistance, -60);
            }
            else
            {
                this.renderColonyNameOnSign(state.colonyName, matrixStack, submitNodeCollector, state, state.colonyDistance, -35);
            }
        }
        matrixStack.popPose();
    }

    /**
     * Submits the name and distance on the sign at offset.
     * @param colonyName the name.
     * @param matrixStack the stack.
     * @param submitNodeCollector the submit collector.
     * @param state the render state.
     * @param distance the distance to the colony.
     * @param offset the offset to render it at.
     */
    private void renderColonyNameOnSign(
      final String colonyName,
      final PoseStack matrixStack,
      final SubmitNodeCollector submitNodeCollector,
      final TileEntityColonySignRenderer.State state,
      final int distance,
      final int offset)
    {
        final int textWidth = this.font.width(colonyName);
        if (textWidth > 90)
        {
            final List<FormattedText> splitName = this.font.getSplitter().splitLines(colonyName, 90, Style.EMPTY);
            for (int i = 0; i < Math.min(2, splitName.size()); i++)
            {
                this.renderText(matrixStack, submitNodeCollector, state, splitName.get(i).getString(), i, offset);
            }
            this.renderText(matrixStack, submitNodeCollector, state,
              Component.translatable("com.minecolonies.coremod.dist.blocks", distance).getString(), 3, offset);
        }
        else
        {
            this.renderText(matrixStack, submitNodeCollector, state, colonyName, 0, offset);
            this.renderText(matrixStack, submitNodeCollector, state,
              Component.translatable("com.minecolonies.coremod.dist.blocks", distance).getString(), 3, offset);
        }
    }

    /**
     * Text submit utility.
     * @param matrixStack the matrix stack.
     * @param submitNodeCollector the submit collector.
     * @param state the render state.
     * @param text the text to render.
     * @param line the line of the text.
     * @param offset additional offset.
     */
    private void renderText(
      final PoseStack matrixStack,
      final SubmitNodeCollector submitNodeCollector,
      final TileEntityColonySignRenderer.State state,
      String text,
      final int line,
      final float offset)
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
              line * 8f + offset,
              iReorderingProcessor,
              false,
              Font.DisplayMode.NORMAL,
              state.lightCoords,
              0xdcdcdc00,
              0,
              0);
        }
    }

    /**
     * PORT26: shouldRenderOffScreen lost its parameter.
     */
    @Override
    public boolean shouldRenderOffScreen()
    {
        return false;
    }

    public static void renderSignHover(final WorldEventContext context)
    {
        final HitResult rayTraceResult = Minecraft.getInstance().hitResult;
        if (!(rayTraceResult instanceof final BlockHitResult blockRayTraceResult) || blockRayTraceResult.getType() == HitResult.Type.MISS)
            return;

        final BlockPos posAtCamera = blockRayTraceResult.getBlockPos();
        if (context.clientLevel.getBlockState(posAtCamera).getBlock() != ModBlocks.blockColonySign)
        {
            return;
        }

        if (context.clientLevel.getBlockEntity(posAtCamera) instanceof TileEntityColonySign tileEntityColonySign)
        {
            if (!BlockPos.ZERO.equals(tileEntityColonySign.getPreviousPos()))
            {
                renderTextBoxAtPos(context, tileEntityColonySign.getPreviousPos(), List.of(Component.translatable(PREVIOUS).getString()));
            }
            if (!BlockPos.ZERO.equals(tileEntityColonySign.getNextPosition()))
            {
                renderTextBoxAtPos(context, tileEntityColonySign.getNextPosition(), List.of(Component.translatable(NEXT).getString()));
            }
        }
    }

    private static void renderTextBoxAtPos(final WorldEventContext context, final BlockPos pos, final List<String> text)
    {
        context.pushPoseCameraToPos(pos);
        context.renderLineBoxWithShadow(BlockPos.ZERO, 0xffffffff, WorldEventContext.DEFAULT_LINE_WIDTH * 2);
        context.popPose();
        renderDebugText(pos, text, context.poseStack, true, 3, 2.5f, context.bufferSource, context.cameraPosition, context.cameraOrientation);
    }

    public static void renderDebugText(final BlockPos renderPos,
        final List<String> text,
        final PoseStack matrixStack,
        final boolean forceWhite,
        final int mergeEveryXListElements,
        final float scale,
        final net.minecraft.client.renderer.MultiBufferSource buffer, final Vec3 cameraPosition, final org.joml.Quaternionf cameraOrientation)
    {
        if (mergeEveryXListElements < 1)
        {
            throw new IllegalArgumentException("mergeEveryXListElements is less than 1");
        }

        final int cap = text.size();
        if (cap > 0)
        {
            final Font fontrenderer = Minecraft.getInstance().font;

            matrixStack.pushPose();
            matrixStack.translate(renderPos.getX() + 0.5d - cameraPosition.x(), renderPos.getY() + 0.6d - cameraPosition.y(), renderPos.getZ() + 0.5d - cameraPosition.z());
            // PORT26: EntityRenderDispatcher#cameraOrientation is gone — the camera rotation
            // comes from the camera render state (carried by the world event context).
            matrixStack.mulPose(cameraOrientation);
            matrixStack.scale(0.014f, -0.014f, 0.014f);

            final float backgroundTextOpacity = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
            final int alphaMask = (int) (backgroundTextOpacity * 255.0F) << 24;

            final org.joml.Matrix4f rawPosMatrix = matrixStack.last().pose();
            rawPosMatrix.scale(scale, scale, scale);

            for (int i = 0; i < cap; i += mergeEveryXListElements)
            {
                final MutableComponent renderText = Component.literal(
                    mergeEveryXListElements == 1 ? text.get(i) : text.subList(i, Math.min(i + mergeEveryXListElements, cap)).toString());
                final float textCenterShift = (float) (-fontrenderer.width(renderText) / 2);

                fontrenderer.drawInBatch(renderText,
                    textCenterShift,
                    0,
                    forceWhite ? 0xffffffff : 0x20ffffff,
                    false,
                    rawPosMatrix,
                    buffer,
                    Font.DisplayMode.SEE_THROUGH,
                    alphaMask,
                    0x00f000f0);
                if (!forceWhite)
                {
                    fontrenderer.drawInBatch(renderText, textCenterShift, 0, 0xffffffff, false, rawPosMatrix, buffer, Font.DisplayMode.NORMAL, 0, 0x00f000f0);
                }
                matrixStack.translate(0.0d, fontrenderer.lineHeight + 1, 0.0d);
            }

            matrixStack.popPose();
        }
    }
}
