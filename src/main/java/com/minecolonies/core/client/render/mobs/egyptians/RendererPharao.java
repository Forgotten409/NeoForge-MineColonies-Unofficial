package com.minecolonies.core.client.render.mobs.egyptians;

import com.minecolonies.api.client.render.modeltype.RaiderRenderState;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesMonster;
import com.minecolonies.core.client.model.raiders.ModelPharaoh;
import com.minecolonies.core.event.ClientRegistryHandler;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Renderer used for the pharao.
 *
 * <p>PORT26: render-state rewrite — texture resolution moved from
 * {@code getTextureLocation(entity)} into {@code extractRenderState} (the state
 * carries the resolved texture; vanilla precedent for entity-dependent textures).</p>
 */
public class RendererPharao extends AbstractRendererEgyptian<AbstractEntityMinecoloniesMonster, ModelPharaoh>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("minecolonies", "textures/entity/raiders/pharao.png");

    /**
     * Constructor method for renderer
     *
     * @param context the renderManager
     */
    public RendererPharao(final EntityRendererProvider.Context context)
    {
        super(context, new ModelPharaoh(context.bakeLayer(ClientRegistryHandler.PHARAO)), 0.5F);
    }

    @Override
    public void extractRenderState(@NotNull final AbstractEntityMinecoloniesMonster entity, @NotNull final RaiderRenderState state, final float partialTicks)
    {
        super.extractRenderState(entity, state, partialTicks);
        state.texture = TEXTURE;
    }

    @NotNull
    @Override
    public Identifier getTextureLocation(@NotNull final RaiderRenderState state)
    {
        return state.texture;
    }
}
