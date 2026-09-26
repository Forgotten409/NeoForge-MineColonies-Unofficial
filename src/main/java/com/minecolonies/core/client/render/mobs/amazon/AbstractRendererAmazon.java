package com.minecolonies.core.client.render.mobs.amazon;

import com.minecolonies.api.client.render.modeltype.RaiderRenderState;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesMonster;
import com.minecolonies.core.client.render.RenderUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.InteractionHand;

/**
 * Abstract for rendering Amazons.
 *
 * <p>PORT26: render-state rewrite — the renderer now operates on
 * {@link RaiderRenderState} instead of the entity, and the arm poses that were set on
 * the model in the old {@code render()} override are extracted into the state.</p>
 */
public abstract class AbstractRendererAmazon<T extends AbstractEntityMinecoloniesMonster, M extends HumanoidModel<RaiderRenderState>> extends HumanoidMobRenderer<T, RaiderRenderState, M>
{
    public AbstractRendererAmazon(final EntityRendererProvider.Context context, final M modelBipedIn, final float shadowSize)
    {
        super(context, modelBipedIn, shadowSize);
        // PORT26: ItemInHandLayer is already added by the HumanoidMobRenderer constructor; the
        // armor layer now takes an ArmorModelSet (the PLAYER_INNER/OUTER_ARMOR layers are gone,
        // armor renders data-driven through the player equipment assets) + the equipment renderer.
        this.addLayer(new HumanoidArmorLayer<>(this,
            ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new),
            context.getEquipmentRenderer()));
    }

    @Override
    public RaiderRenderState createRenderState()
    {
        // PORT26: render-state architecture.
        return new RaiderRenderState();
    }

    @Override
    public void extractRenderState(final T raider, final RaiderRenderState state, final float partialTicks)
    {
        super.extractRenderState(raider, state, partialTicks);
        // PORT26: texture variant of the raider (used by the concrete renderers to resolve the texture).
        state.textureId = raider.getTextureId();
        // PORT26: arm poses were set on the model in the old render() override.
        state.rightArmPose = RenderUtils.getArmPose(raider, InteractionHand.MAIN_HAND);
        state.leftArmPose = RenderUtils.getArmPose(raider, InteractionHand.OFF_HAND);
    }
}
