package com.minecolonies.core.client.render;

import com.minecolonies.core.blocks.BlockDecorationController;
import com.minecolonies.core.tileentities.TileEntityDecorationController;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.extensions.SubmitNodeStorageExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * PORT26: render-state rewrite — the decoration controller shape logic is extracted into
 * {@link State} (which block state to render and where); the block itself is submitted
 * through the NeoForge multi-layer block model submit (the old
 * {@code ModelBlockRenderer#tesselateBlock} path with block renderer caching is gone —
 * same pattern as the structurize tag anchor renderer).
 */
public class TileEntityDecoControllerRenderer implements BlockEntityRenderer<BlockEntity, TileEntityDecoControllerRenderer.State>
{
    /**
     * PORT26: render state of the decoration controller.
     */
    public static class State extends BlockEntityRenderState
    {
        public boolean render;
        public BlockState decoControllerState;
        public Vec3 translation = Vec3.ZERO;
    }

    public TileEntityDecoControllerRenderer(final BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public TileEntityDecoControllerRenderer.State createRenderState()
    {
        return new TileEntityDecoControllerRenderer.State();
    }

    @Override
    public void extractRenderState(
      @NotNull final BlockEntity blockEntity,
      @NotNull final TileEntityDecoControllerRenderer.State state,
      final float partialTicks,
      final Vec3 cameraPosition,
      @Nullable final ModelFeatureRenderer.CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);

        state.render = false;
        state.translation = Vec3.ZERO;
        if (!(blockEntity instanceof TileEntityDecorationController decorationController))
        {
            return;
        }

        final var level = blockEntity.getLevel();
        if (level == null)
        {
            return;
        }

        final BlockState decoController = decorationController.getBlockState();
        final Direction direction = decoController.getValue(BlockDecorationController.FACING);
        final BlockPos offsetPos = blockEntity.getBlockPos().relative(direction);
        final BlockState neighborState = level.getBlockState(offsetPos);
        final VoxelShape shape = neighborState.getShape(level, offsetPos);
        if (shape.isEmpty() || Block.isShapeFullBlock(shape))
        {
            state.render = true;
            state.decoControllerState = decoController;
            state.translation = Vec3.ZERO;
            return;
        }

        final Vec3 translateVec = switch (direction)
                                    {
                                        case UP -> new Vec3(0, shape.min(Direction.Axis.Y), 0);
                                        case DOWN -> new Vec3(0, shape.max(Direction.Axis.Y)-1, 0);
                                        case NORTH -> new Vec3(0, 0, shape.max(Direction.Axis.Z)-1);
                                        case SOUTH -> new Vec3(0, 0, shape.min(Direction.Axis.Z));
                                        case EAST -> new Vec3( shape.min(Direction.Axis.X), 0, 0);
                                        case WEST -> new Vec3(shape.max(Direction.Axis.X)-1, 0, 0);
                                    };

        if (!decoController.isAir())
        {
            state.render = true;
            state.decoControllerState = decoController;
            state.translation = translateVec;
        }
    }

    @Override
    public void submit(
      @NotNull final TileEntityDecoControllerRenderer.State state,
      @NotNull final PoseStack matrixStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        if (!state.render)
        {
            return;
        }

        matrixStack.pushPose();
        matrixStack.translate(state.translation.x, state.translation.y, state.translation.z);

        // PORT26: multi-layer block model submit (was ModelBlockRenderer#tesselateBlock).
        final Minecraft mc = Minecraft.getInstance();
        final BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state.decoControllerState);
        final List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(), parts);
        if (!parts.isEmpty() && submitNodeCollector instanceof final SubmitNodeStorageExtension extension)
        {
            extension.submitMultiLayerBlockModel(
              matrixStack,
              parts,
              model.hasMaterialFlag(BakedQuad.FLAG_TRANSLUCENT),
              new int[0],
              state.lightCoords,
              OverlayTexture.NO_OVERLAY,
              0);
        }

        matrixStack.popPose();
    }
}
