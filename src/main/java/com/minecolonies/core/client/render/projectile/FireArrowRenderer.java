package com.minecolonies.core.client.render.projectile;

import com.minecolonies.api.util.constant.Constants;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.jetbrains.annotations.NotNull;

/**
 * Custom renderer for the fire arrows.
 *
 * <p>PORT26: render-state rewrite — {@code ArrowRenderer} now takes
 * {@code <T extends AbstractArrow, S extends ArrowRenderState>}; the animated texture
 * index (previously {@code entity.tickCount % 6} inside getTextureLocation(entity)) is
 * extracted into a small render state subclass.</p>
 */
public class FireArrowRenderer extends ArrowRenderer<AbstractArrow, FireArrowRenderer.State>
{
    /**
     * Array of different textures.
     */
    private static final Identifier[] RES = new Identifier[]
                                                     {
                                                       Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/item/magicalarrows/magical_arrow1.png"),
                                                       Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/item/magicalarrows/magical_arrow2.png"),
                                                       Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/item/magicalarrows/magical_arrow3.png"),
                                                       Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/item/magicalarrows/magical_arrow4.png"),
                                                       Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/item/magicalarrows/magical_arrow5.png"),
                                                       Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/item/magicalarrows/magical_arrow6.png")
                                                     };

    /**
     * PORT26: render state carrying the animation frame of the fire arrow texture.
     */
    public static class State extends ArrowRenderState
    {
        public int textureIndex;
    }

    public FireArrowRenderer(final EntityRendererProvider.Context context)
    {
        super(context);
    }

    @NotNull
    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(@NotNull final AbstractArrow entity, @NotNull final State state, final float partialTicks)
    {
        super.extractRenderState(entity, state, partialTicks);
        // PORT26: the fire arrow texture animates by entity tick count.
        state.textureIndex = entity.tickCount % RES.length;
    }

    @NotNull
    @Override
    public Identifier getTextureLocation(@NotNull final State state)
    {
        return RES[state.textureIndex];
    }
}
