package com.ldtteam.domumornamentum.client.render;

import com.ldtteam.domumornamentum.util.ItemStackUtils;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Renders a translucent "ghost" preview of the block the player is about to place.
 *
 * <p>PORT26: fully rewritten for the new model/render system:
 * <ul>
 *   <li>{@code BakedModel#getQuads(state, dir, random, modelData, renderType)} is gone — the model is
 *       obtained from {@code ModelManager#getBlockStateModelSet()} and its parts are collected via
 *       {@link BlockStateModel#collectParts(RandomSource, List)}.</li>
 *   <li>{@code ItemRenderer} (used for the item-model path) was removed entirely — the item branch now
 *       falls back to rendering the block model of the placement state as well.</li>
 *   <li>Quad upload goes through {@code VertexConsumer#putBakedQuad(PoseStack.Pose, BakedQuad, QuadInstance)}.</li>
 *   <li>The custom ghost RenderTypes are mapped to {@link RenderTypes#translucentMovingBlock()}.</li>
 * </ul>
 * Per-block material retexturing of the ghost (old ModelData plumbing) is not yet re-implemented —
 * the ghost currently shows the model's default textures.
 */
public class ModelGhostRenderer {

    private static final ModelGhostRenderer INSTANCE = new ModelGhostRenderer();

    private static final ByteBufferBuilder BUFFER_BUILDER = new ByteBufferBuilder(2097152);

    public static ModelGhostRenderer getInstance() {
        return INSTANCE;
    }

    private ModelGhostRenderer() {
    }

    public void renderGhost(
            final PoseStack poseStack,
            final ItemStack renderStack,
            final Vec3 targetedRenderPos,
            final BlockHitResult blockHitResult,
            final ClientLevel level,
            final boolean ignoreDepth) {
        poseStack.pushPose();

        // Offset/scale by an unnoticeable amount to prevent z-fighting
        // PORT26: Camera#getPosition() was renamed to position()
        final Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        poseStack.translate(
                targetedRenderPos.x - camera.x - 0.000125,
                targetedRenderPos.y - camera.y + 0.000125,
                targetedRenderPos.z - camera.z - 0.000125
        );
        poseStack.scale(1.001F, 1.001F, 1.001F);

        final Vector4f color = new Vector4f(0, 0, 1, 0.5f);

        BlockState placementState;
        if (renderStack.getItem() instanceof BlockItem blockItem) {
            final BlockPlaceContext context = new BlockPlaceContext(
                    Objects.requireNonNull(Minecraft.getInstance().player),
                    Objects.requireNonNull(ItemStackUtils.getHandWithMateriallyTexturedItemStackFromPlayer(Minecraft.getInstance().player)),
                    renderStack,
                    blockHitResult
            );
            placementState = blockItem.getBlock().getStateForPlacement(context);

            if (placementState == null) {
                poseStack.popPose();
                return;
            }

            placementState = renderStack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).apply(placementState);
        } else {
            // PORT26: ItemRenderer#getModel path removed — no reliable generic item-model ghost;
            // skip non-block items.
            poseStack.popPose();
            return;
        }

        renderGhost(
                placementState,
                blockHitResult.getBlockPos(),
                poseStack,
                level,
                color,
                false
        );

        poseStack.popPose();
    }

    @SuppressWarnings("SameParameterValue")
    private void renderGhost(
            final BlockState state,
            final BlockPos pos,
            final PoseStack poseStack,
            final ClientLevel level,
            final Vector4f color,
            final boolean renderColoredGhost) {
        final RenderType renderType = renderColoredGhost
                ? ModRenderTypes.GHOST_BLOCK_COLORED_PREVIEW.get()
                : ModRenderTypes.GHOST_BLOCK_PREVIEW.get();

        final BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        if (model == null) {
            return;
        }

        final BufferBuilder buffer = new BufferBuilder(BUFFER_BUILDER, renderType.mode(), renderType.format());
        if (renderColoredGhost) {
            renderColoredModel(model, poseStack, color, buffer);
        } else {
            renderTexturedModel(state, pos, level, model, poseStack, buffer);
        }
        final MeshData meshData = buffer.buildOrThrow();
        renderType.draw(meshData);
        meshData.close();
    }

    /**
     * Renders the model with its own textures, using the vanilla block renderer for AO + tinting.
     * Positions are emitted through the pose stack (already translated to the camera-relative
     * target position by the caller), so the x/y/z offsets handed to the quad output are ignored.
     */
    private static void renderTexturedModel(
            final BlockState state,
            final BlockPos pos,
            final ClientLevel level,
            final BlockStateModel model,
            final PoseStack poseStack,
            final BufferBuilder buffer) {
        final float alpha = 0.5F;
        if (level != null) {
            final ModelBlockRenderer blockRenderer = new ModelBlockRenderer(false, true, Minecraft.getInstance().getBlockColors());
            blockRenderer.tesselateBlock((x, y, z, quad, instance) -> {
                for (int v = 0; v < 4; v++) {
                    final int c = instance.getColor(v);
                    final int a = (int) (((c >>> 24) & 0xFF) * alpha);
                    instance.setColor(v, (a << 24) | (c & 0x00FFFFFF));
                }
                buffer.putBakedQuad(poseStack.last(), quad, instance);
            }, 0.0F, 0.0F, 0.0F, level, pos, state, model, state.getSeed(pos));
        } else {
            final RandomSource random = RandomSource.create(42L);
            final List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(random, parts);
            for (final BlockStateModelPart part : parts) {
                for (final Direction direction : Direction.values()) {
                    emitQuads(part.getQuads(direction), poseStack, buffer);
                }
                emitQuads(part.getQuads(null), poseStack, buffer);
            }
        }
    }

    private static void emitQuads(final List<net.minecraft.client.resources.model.geometry.BakedQuad> quads, final PoseStack poseStack, final BufferBuilder buffer) {
        if (quads == null) {
            return;
        }
        for (final net.minecraft.client.resources.model.geometry.BakedQuad quad : quads) {
            final QuadInstance instance = new QuadInstance();
            buffer.putBakedQuad(poseStack.last(), quad, instance);
        }
    }

    private static final float[] DIRECTIONAL_BRIGHTNESS = { 0.5f, 1f, 0.7f, 0.7f, 0.6f, 0.6f };

    /**
     * Optimized version of the old ItemRenderer#renderModelLists that ignores textures and renders
     * the model's quads with a single RGBA color shaded by the quads' direction to match MC's shading.
     */
    private static void renderColoredModel(
            final BlockStateModel model,
            final PoseStack poseStack,
            final Vector4f color,
            final BufferBuilder buffer) {
        final RandomSource random = RandomSource.create(42L);
        final List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);

        for (final BlockStateModelPart part : parts) {
            for (final Direction direction : Direction.values()) {
                emitColoredQuads(part.getQuads(direction), direction, poseStack, color, buffer);
            }
            // Quads of unspecified direction keep the ambient brightness of "up".
            emitColoredQuads(part.getQuads(null), Direction.UP, poseStack, color, buffer);
        }
    }

    private static void emitColoredQuads(
            final List<net.minecraft.client.resources.model.geometry.BakedQuad> quads,
            final Direction fallbackDirection,
            final PoseStack poseStack,
            final Vector4f color,
            final BufferBuilder buffer) {
        if (quads == null) {
            return;
        }
        for (final net.minecraft.client.resources.model.geometry.BakedQuad quad : quads) {
            final Direction quadDirection = quad.direction() != null ? quad.direction() : fallbackDirection;
            final float brightness = DIRECTIONAL_BRIGHTNESS[quadDirection.get3DDataValue()];
            final int argb = ((int) (color.w() * 255.0F) << 24)
                    | ((int) (color.x() * brightness * 255.0F) << 16)
                    | ((int) (color.y() * brightness * 255.0F) << 8)
                    | (int) (color.z() * brightness * 255.0F);
            final QuadInstance instance = new QuadInstance();
            for (int v = 0; v < 4; v++) {
                instance.setColor(v, argb);
            }
            buffer.putBakedQuad(poseStack.last(), quad, instance);
        }
    }

}
