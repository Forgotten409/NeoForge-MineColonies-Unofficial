package com.minecolonies.core.client.render.mobs;

import com.minecolonies.api.client.render.modeltype.RaiderRenderState;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.client.model.MercenaryModel;
import com.minecolonies.core.event.ClientRegistryHandler;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.PathfinderMob;
import org.jetbrains.annotations.NotNull;

/**
 * Renderer for EntityMercenary.
 *
 * <p>PORT26: render-state rewrite — {@code HumanoidMobRenderer} now operates on a render
 * state ({@link RaiderRenderState}); the held-item layer is added by its constructor and
 * the armor layer takes an ArmorModelSet + equipment renderer.</p>
 */
public class RenderMercenary extends HumanoidMobRenderer<PathfinderMob, RaiderRenderState, MercenaryModel>
{
    /**
     * Texture of the entity.
     */
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/entity/citizen/default/settlermale1_b.png");

    /**
     * Renders the mercenary mobs, with an held item and armorset.
     *
     * @param context RenderManager
     */
    public RenderMercenary(final EntityRendererProvider.Context context)
    {
        super(context, new MercenaryModel(context.bakeLayer(ClientRegistryHandler.MERCENARY)), 0.5f);

        // PORT26: ItemInHandLayer is already added by HumanoidMobRenderer; the armor layer takes an
        // ArmorModelSet + equipment renderer (PLAYER_INNER/OUTER_ARMOR layers are gone).
        this.addLayer(new HumanoidArmorLayer<>(this,
            ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new),
            context.getEquipmentRenderer()));
    }

    @NotNull
    @Override
    public RaiderRenderState createRenderState()
    {
        // PORT26: render-state architecture.
        return new RaiderRenderState();
    }

    @NotNull
    @Override
    public Identifier getTextureLocation(@NotNull final RaiderRenderState state)
    {
        return TEXTURE;
    }
}
