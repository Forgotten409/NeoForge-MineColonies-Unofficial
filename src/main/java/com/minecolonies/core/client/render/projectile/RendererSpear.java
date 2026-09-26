package com.minecolonies.core.client.render.projectile;

import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.client.model.SpearModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ThrownTridentRenderState;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import org.jetbrains.annotations.NotNull;

/**
 * Custom renderer for spears
 *
 * <p>PORT26: render-state rewrite, mirrors the vanilla {@code ThrownTridentRenderer}:
 * rotations and the foil flag are extracted into a {@link ThrownTridentRenderState}
 * (it carries exactly the fields this renderer needs) and the model is submitted
 * through the submit collector, with the foil pass via
 * {@link ItemFeatureRenderer#getFoilRenderType}.</p>
 */
public class RendererSpear extends EntityRenderer<ThrownTrident, ThrownTridentRenderState>
{
    private final Identifier texture = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/entity/spear.png");
    private final SpearModel model;

    /**
     * Create a new spear renderer.
     * @param context the context.
     */
    public RendererSpear(final EntityRendererProvider.Context context)
    {
        super(context);
        this.model = new SpearModel(context.bakeLayer(ModelLayers.TRIDENT));
    }

    @Override
    public void submit(
      @NotNull final ThrownTridentRenderState state,
      @NotNull final PoseStack stack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(state.yRot - 90.0F));
        stack.mulPose(Axis.ZP.rotationDegrees(state.xRot + 90.0F));
        submitNodeCollector.order(0)
          .submitModel(this.model, Unit.INSTANCE, stack, this.texture, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
        if (state.isFoil)
        {
            // PORT26: foil pass — ItemRenderer.getFoilBufferDirect is gone, use the vanilla feature renderer helper.
            submitNodeCollector.order(1)
              .submitModel(
                this.model,
                Unit.INSTANCE,
                stack,
                ItemFeatureRenderer.getFoilRenderType(this.model.renderType(this.texture), false),
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                state.outlineColor,
                null);
        }
        stack.popPose();
        super.submit(state, stack, submitNodeCollector, camera);
    }

    @NotNull
    @Override
    public ThrownTridentRenderState createRenderState()
    {
        return new ThrownTridentRenderState();
    }

    @Override
    public void extractRenderState(@NotNull final ThrownTrident entity, @NotNull final ThrownTridentRenderState state, final float partialTicks)
    {
        super.extractRenderState(entity, state, partialTicks);
        // PORT26: rotations + foil extracted from the entity (was inline in the old render()).
        state.yRot = entity.getYRot(partialTicks);
        state.xRot = entity.getXRot(partialTicks);
        state.isFoil = entity.isFoil();
    }
}
