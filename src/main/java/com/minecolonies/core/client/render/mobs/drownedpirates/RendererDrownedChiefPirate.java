package com.minecolonies.core.client.render.mobs.drownedpirates;

import com.minecolonies.api.client.render.modeltype.RaiderRenderState;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesMonster;
import net.minecraft.client.model.HumanoidModel;
import com.minecolonies.core.event.ClientRegistryHandler; // PORT26 FIX (no faces): legacy 64x32 raider layer
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Renderer used for Chief Barbarians.
 *
 * <p>PORT26: render-state rewrite — texture resolution moved from
 * {@code getTextureLocation(entity)} into {@code extractRenderState} (the state
 * carries the resolved texture; vanilla precedent for entity-dependent textures).</p>
 */
public class RendererDrownedChiefPirate extends AbstractRendererDrownedPirate<AbstractEntityMinecoloniesMonster, HumanoidModel<RaiderRenderState>>
{
    private static final Identifier TEXTURE1 = Identifier.fromNamespaceAndPath("minecolonies", "textures/entity/raiders/drowned_pirate_nude.png");

    /**
     * Constructor method for renderer
     *
     * @param context the renderManager
     */
    public RendererDrownedChiefPirate(final EntityRendererProvider.Context context)
    {
        super(context, new HumanoidModel<>(context.bakeLayer(ClientRegistryHandler.RAIDER_LEGACY)), 0.5F);
    }

    @Override
    public void extractRenderState(@NotNull final AbstractEntityMinecoloniesMonster entity, @NotNull final RaiderRenderState state, final float partialTicks)
    {
        super.extractRenderState(entity, state, partialTicks);
        state.texture = TEXTURE1;
    }

    @NotNull
    @Override
    public Identifier getTextureLocation(@NotNull final RaiderRenderState state)
    {
        return state.texture;
    }
}
