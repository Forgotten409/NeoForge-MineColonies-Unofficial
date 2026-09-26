package com.minecolonies.api.client.render.modeltype;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * PORT26: render state for minecolonies raiders (barbarians, pirates, egyptians, norsemen, amazons).
 */
public class RaiderRenderState extends HumanoidRenderState
{
    // ------------------------------------------------------------------
    // PORT26: renderer-extracted data (filled by the raider renderers).
    // ------------------------------------------------------------------

    /**
     * PORT26: texture variant of the raider (entity.getTextureId()) — the renderers
     * previously switched textures on the entity inside getTextureLocation(entity);
     * the 26.1 contract receives only the render state, so the variant id is carried here.
     */
    public int textureId;

    /**
     * PORT26: resolved texture of this raider, extracted in extractRenderState —
     * the texture lambdas of the 1.21.1 renderers depended on entity data, which the
     * 26.1 getTextureLocation(state) no longer has access to (vanilla precedent:
     * renderers that resolve textures per entity store them on the state).
     */
    public Identifier texture;
}
