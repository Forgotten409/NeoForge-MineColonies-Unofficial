package com.ldtteam.structurize.client;

import com.ldtteam.structurize.api.Log;
import com.ldtteam.structurize.component.CapturedBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.SubmitNodeStorageExtension;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Tag anchor renderer; renders replacement block model inside of anchor "overlay" model.
 *
 * <p>PORT26: full rewrite. The 1.21.1 class extended
 * {@code BlockEntityWithoutLevelRenderer} (removed — item rendering is data-driven now, the
 * item-side path through {@code IClientItemExtensions#getCustomRenderer} is gone) and drew
 * blocks through {@code BlockRenderDispatcher#renderSingleBlock} (also removed). The 26.1
 * block-entity-renderer contract is extract/submit with render states:
 * <ul>
 *   <li>{@link #extractRenderState} copies the captured replacement onto the render state;</li>
 *   <li>{@link #submit} submits the replacement's block model through the NeoForge
 *       multi-layer block model submit (per-quad render types, block atlas), which is the
 *       same path vanilla uses for moving blocks.</li>
 *   <li>Captured block entities (chests captured in tag anchors) are re-instantiated in
 *       {@link #extractRenderState} and their render states extracted through the vanilla
 *       dispatcher ({@link #extractCapturedBlockEntityState}); {@link #submit} then submits
 *       them nested, with the same scaled pose — the 26.1 equivalent of the 1.21.1
 *       {@code BlockEntityRenderDispatcher#render} call.</li>
 * </ul>
 */
public class TagSubstitutionRenderer implements BlockEntityRenderer<com.ldtteam.structurize.blockentities.BlockEntityTagSubstitution, TagSubstitutionRenderer.RenderState>
{
    /**
     * Render state carrying the captured replacement block for this anchor.
     */
    public static class RenderState extends BlockEntityRenderState
    {
        CapturedBlock replacement = CapturedBlock.EMPTY;

        /**
         * Render state of the block entity captured in the anchor (chests etc.), extracted
         * fresh every frame; null when the anchor captured no block entity or extraction
         * failed.
         */
        @Nullable BlockEntityRenderState capturedBERenderState = null;
    }

    /**
     * @param context the vanilla renderer context (unused — all state is pulled from
     *                {@code Minecraft} at submit time).
     */
    public TagSubstitutionRenderer(final BlockEntityRendererProvider.Context context)
    {
        // no-op: the 1.21.1 superclass init (BlockEntityWithoutLevelRenderer) is gone and the
        // context is not needed by the extract/submit implementation
    }

    @Override
    public RenderState createRenderState()
    {
        return new RenderState();
    }

    @Override
    public void extractRenderState(final com.ldtteam.structurize.blockentities.BlockEntityTagSubstitution entity,
      final RenderState state,
      final float partialTicks,
      final net.minecraft.world.phys.Vec3 cameraPosition,
      final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        // PORT26: extractRenderState is a default interface method (fills the base state via
        // BlockEntityRenderState.extractBase); invoke it through the interface, not super.
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
        state.replacement = entity.getReplacement();
        state.capturedBERenderState = extractCapturedBlockEntityState(entity, partialTicks);
    }

    /**
     * Re-instantiates the block entity captured in this tag anchor (e.g. a chest) and extracts
     * its render state through the vanilla dispatcher — mirroring the 1.21.1 renderer which
     * drew the captured BE through {@code BlockEntityRenderDispatcher#render}. The captured BE
     * lives at the anchor's own position (so light/distance checks resolve against the same
     * block), in the anchor's level: the real world for placed anchors, the blueprint fake
     * level for non-"render nice" previews — the dispatcher is prepared with the matching
     * camera space in both flows.
     *
     * @return the extracted render state, or null when the anchor captured no block entity
     */
    private static @Nullable BlockEntityRenderState extractCapturedBlockEntityState(
      final com.ldtteam.structurize.blockentities.BlockEntityTagSubstitution entity,
      final float partialTicks)
    {
        final CapturedBlock replacement = entity.getReplacement();
        if (replacement.serializedBE().isEmpty())
        {
            return null;
        }
        final Level level = entity.getLevel();
        if (level == null)
        {
            return null;
        }

        try
        {
            final BlockEntity capturedBlockEntity = BlockEntity.loadStatic(
              entity.getBlockPos(), replacement.blockState(), replacement.serializedBE().get(), level.registryAccess());
            if (capturedBlockEntity == null)
            {
                return null;
            }
            capturedBlockEntity.setLevel(level);

            return Minecraft.getInstance().getBlockEntityRenderDispatcher()
              .tryExtractRenderState(capturedBlockEntity, partialTicks, null);
        }
        catch (final Exception e)
        {
            Log.getLogger().error("Failed to extract the block entity captured in the tag anchor at {}", entity.getBlockPos(), e);
            return null;
        }
    }

    @Override
    public void submit(final RenderState state, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera)
    {
        final CapturedBlock replacement = state.replacement;
        if (replacement.blockState().isAir())
        {
            return;
        }

        poseStack.pushPose();
        poseStack.scale(0.995f, 0.995f, 0.995f);
        poseStack.translate(0.0025f, 0.0025f, 0.0025f);

        try
        {
            final Minecraft mc = Minecraft.getInstance();
            final BlockStateModelSet modelSet = mc.getModelManager().getBlockStateModelSet();
            final BlockState blockState = replacement.blockState();
            final BlockStateModel model = modelSet.get(blockState);

            final List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(RandomSource.create(), parts);
            if (!parts.isEmpty() && submitNodeCollector instanceof final SubmitNodeStorageExtension extension)
            {
                extension.submitMultiLayerBlockModel(poseStack,
                  parts,
                  model.hasMaterialFlag(BakedQuad.FLAG_TRANSLUCENT),
                  buildTintLayers(mc.getBlockColors(), blockState, parts),
                  state.lightCoords,
                  OverlayTexture.NO_OVERLAY,
                  0);
            }

            // PORT26: the captured block entity (chests etc.) — submitted nested through the
            // vanilla dispatcher, with the same scaled pose the 1.21.1 renderer used when it
            // called BlockEntityRenderDispatcher#render from inside its own render call.
            if (state.capturedBERenderState != null)
            {
                mc.getBlockEntityRenderDispatcher()
                  .submit(state.capturedBERenderState, poseStack, submitNodeCollector, camera);
            }
        }
        finally
        {
            poseStack.popPose();
        }
    }

    /**
     * Builds the tint layer array indexed by tint index (vanilla contract for the multi-layer
     * block model submit). Tints are resolved without a world context, exactly like the old
     * {@code renderSingleBlock} call did.
     */
    private static int[] buildTintLayers(final BlockColors blockColors, final BlockState blockState, final List<BlockStateModelPart> parts)
    {
        int maxTintIndex = -1;
        for (final BlockStateModelPart part : parts)
        {
            for (final BakedQuad quad : part.getQuads(null))
            {
                if (quad.materialInfo().isTinted())
                {
                    maxTintIndex = Math.max(maxTintIndex, quad.materialInfo().tintIndex());
                }
            }
        }
        if (maxTintIndex < 0)
        {
            return new int[0];
        }

        final int[] tintLayers = new int[maxTintIndex + 1];
        for (int tintIndex = 0; tintIndex <= maxTintIndex; tintIndex++)
        {
            final BlockTintSource tintSource = blockColors.getTintSource(blockState, tintIndex);
            tintLayers[tintIndex] = tintSource != null ? tintSource.color(blockState) : -1;
        }
        return tintLayers;
    }
}
