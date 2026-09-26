package com.minecolonies.core.client.render;

import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.jetbrains.annotations.NotNull;

/**
 * Renderer for a normal tile entity (Nothing special with rendering).
 *
 * <p>PORT26: render-state architecture — the renderer is generic over its (empty) render
 * state and submits nothing.</p>
 */
public class EmptyTileEntitySpecialRenderer implements BlockEntityRenderer<AbstractTileEntityColonyBuilding, EmptyTileEntitySpecialRenderer.State>
{
    /**
     * PORT26: empty render state.
     */
    public static class State extends BlockEntityRenderState
    {
    }

    public EmptyTileEntitySpecialRenderer(final BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public EmptyTileEntitySpecialRenderer.State createRenderState()
    {
        return new EmptyTileEntitySpecialRenderer.State();
    }

    @Override
    public void submit(
      @NotNull final EmptyTileEntitySpecialRenderer.State state,
      @NotNull final PoseStack matrixStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        // nothing to render
    }
}
