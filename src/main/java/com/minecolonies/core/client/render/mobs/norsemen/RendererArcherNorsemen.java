package com.minecolonies.core.client.render.mobs.norsemen;

import com.minecolonies.api.client.render.modeltype.RaiderRenderState;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesMonster;
import com.minecolonies.core.client.model.raiders.ModelArcherNorsemen;
import com.minecolonies.core.event.ClientRegistryHandler;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Renderer used for archer amazons.
 *
 * <p>PORT26: render-state rewrite — texture resolution moved from
 * {@code getTextureLocation(entity)} into {@code extractRenderState} (the state
 * carries the resolved texture; vanilla precedent for entity-dependent textures).</p>
 */
public class RendererArcherNorsemen extends AbstractRendererNorsemen<AbstractEntityMinecoloniesMonster, ModelArcherNorsemen>
{
    private static final Identifier TEXTURE1 = Identifier.fromNamespaceAndPath("minecolonies", "textures/entity/raiders/norsemen_archer1.png");
    private static final Identifier TEXTURE2 = Identifier.fromNamespaceAndPath("minecolonies", "textures/entity/raiders/norsemen_archer2.png");

    /**
     * Constructor method for renderer
     *
     * @param context the renderManager
     */
    public RendererArcherNorsemen(final EntityRendererProvider.Context context)
    {
        super(context, new ModelArcherNorsemen(context.bakeLayer(ClientRegistryHandler.NORSEMEN_ARCHER)), 0.5F);
    }

    @Override
    public void extractRenderState(@NotNull final AbstractEntityMinecoloniesMonster entity, @NotNull final RaiderRenderState state, final float partialTicks)
    {
        super.extractRenderState(entity, state, partialTicks);
        // PORT26: texture variant switch — the variant id is carried in the render state.
        if (state.textureId == 1)
        {
            state.texture = TEXTURE2;
        }
        else
        {
            state.texture = TEXTURE1;
        }
    }

    @NotNull
    @Override
    public Identifier getTextureLocation(@NotNull final RaiderRenderState state)
    {
        return state.texture;
    }
}
