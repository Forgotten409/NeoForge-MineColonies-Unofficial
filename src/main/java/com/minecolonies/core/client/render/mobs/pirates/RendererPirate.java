package com.minecolonies.core.client.render.mobs.pirates;

import com.minecolonies.api.client.render.modeltype.RaiderRenderState;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesMonster;
import net.minecraft.client.model.HumanoidModel;
import com.minecolonies.core.event.ClientRegistryHandler; // PORT26 FIX (no faces): legacy 64x32 raider layer
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Renderer used for Barbarians And Archer Barbarians.
 *
 * <p>PORT26: render-state rewrite — texture resolution moved from
 * {@code getTextureLocation(entity)} into {@code extractRenderState} (the state
 * carries the resolved texture; vanilla precedent for entity-dependent textures).</p>
 */
public class RendererPirate extends AbstractRendererPirate<AbstractEntityMinecoloniesMonster, HumanoidModel<RaiderRenderState>>
{
    private static final Identifier TEXTURE1 = Identifier.fromNamespaceAndPath("minecolonies", "textures/entity/raiders/pirate1.png");
    private static final Identifier TEXTURE2 = Identifier.fromNamespaceAndPath("minecolonies", "textures/entity/raiders/pirate2.png");
    private static final Identifier TEXTURE3 = Identifier.fromNamespaceAndPath("minecolonies", "textures/entity/raiders/pirate3.png");
    private static final Identifier TEXTURE4 = Identifier.fromNamespaceAndPath("minecolonies", "textures/entity/raiders/pirate4.png");

    /**
     * Constructor method for renderer
     *
     * @param context the renderManager
     */
    public RendererPirate(final EntityRendererProvider.Context context)
    {
        super(context, new HumanoidModel<>(context.bakeLayer(ClientRegistryHandler.RAIDER_LEGACY)), 0.5F);
    }

    @Override
    public void extractRenderState(@NotNull final AbstractEntityMinecoloniesMonster entity, @NotNull final RaiderRenderState state, final float partialTicks)
    {
        super.extractRenderState(entity, state, partialTicks);
        // PORT26: texture variant switch — the variant id is carried in the render state.
        switch (state.textureId)
        {
            case 0:
                state.texture = TEXTURE1;
                break;
            case 1:
                state.texture = TEXTURE2;
                break;
            case 2:
                state.texture = TEXTURE3;
                break;
            default:
                state.texture = TEXTURE4;
                break;
        }
    }

    @NotNull
    @Override
    public Identifier getTextureLocation(@NotNull final RaiderRenderState state)
    {
        return state.texture;
    }
}
