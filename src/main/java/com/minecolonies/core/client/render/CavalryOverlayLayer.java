package com.minecolonies.core.client.render;

import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.client.render.CavalryHorseRenderer;
import com.minecolonies.core.entity.other.cavalry.CavalryHorseEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.equine.HorseModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HorseRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB; // PORT26: FastColor removed — net.minecraft.util.ARGB is the replacement
import net.minecraft.util.Mth;

import java.util.WeakHashMap;

import javax.annotation.Nonnull;

/**
 * Renders the cavalry horse overlay layer (combat readiness indicator).
 *
 * <p>PORT26: layers no longer see the entity — they render from {@link HorseRenderState}.
 * {@link CavalryHorseRenderer#extractRenderState} publishes the per-entity combat
 * readiness into the {@link #READINESS} map (keyed by the state instance; states are
 * fresh per frame and the weak keys cannot leak), and this layer reads it back during
 * submit.</p>
 */
public class CavalryOverlayLayer extends RenderLayer<HorseRenderState, HorseModel>
{
    /** PORT26: render-state side channel — filled by CavalryHorseRenderer, read here. */
    static final WeakHashMap<HorseRenderState, Float> READINESS = new WeakHashMap<>();

    public CavalryOverlayLayer(final RenderLayerParent<HorseRenderState, HorseModel> parent)
    {
        super(parent);
    }

    static void putReadiness(final HorseRenderState state, final float readiness)
    {
        READINESS.put(state, readiness);
    }

    @Override
    public void submit(
      @Nonnull final PoseStack poseStack,
      @Nonnull final SubmitNodeCollector submitNodeCollector,
      final int lightCoords,
      @Nonnull final HorseRenderState state,
      final float yRot,
      final float xRot)
    {
        final float readiness = READINESS.getOrDefault(state, 0.0f);
        final int segments = Mth.clamp((int) Math.floor(readiness * 5f + 0.0001f), 0, 5);

        final Identifier overlayTex = Identifier.fromNamespaceAndPath(Constants.MOD_ID,
          "textures/entity/horse/cavalry_overlay_layer" + segments + ".png");

        // 0.85f alpha -> 217 (out of 255)
        final int alpha = (int) (0.85f * 255.0f);
        final int color = ARGB.color(alpha, 255, 255, 255); // PORT26: FastColor.ARGB32.color → ARGB.color

        submitNodeCollector.order(1)
          .submitModel(
            this.getParentModel(),
            state,
            poseStack,
            RenderTypes.entityTranslucent(overlayTex),
            lightCoords,
            LivingEntityRenderer.getOverlayCoords(state, 0.0F),
            color,
            null,
            state.outlineColor,
            null
          );
    }
}
