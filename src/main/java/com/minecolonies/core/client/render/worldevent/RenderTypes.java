package com.minecolonies.core.client.render.worldevent;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Mod render types.
 *
 * <p>PORT26: fully rewritten — the 1.21.1 {@code RenderType.create(name, format, mode, ...)}
 * + {@code CompositeState} shard system is gone in 26.1; each render type is now
 * {@code RenderType#create(name, RenderSetup)} on top of a render pipeline.</p>
 *
 * <p>PORT26 FIX v5 (0.4.5, "Missing program minecolonies:pipeline/world_entity_icon in
 * override list" under Iris): the citizen status icons no longer use a custom pipeline.
 * Iris only redirects pipelines that exist in the active pack's program override list —
 * vanilla pipelines are all mapped, custom modded pipelines are not (log spam + the
 * geometry drawn outside the pack's frame integration). The icons now draw through
 * vanilla {@code RenderTypes#textSeeThrough} — the exact pipeline family vanilla name
 * tags use (translucent blend, depth test disabled, lightmap sampled at fullbright),
 * so every shader pack composites them correctly and the warning is gone. The icon
 * vertex emission gained a white color + fullbright light coordinate to match the
 * text format (see {@code RenderBipedCitizen#addIconVertex}).</p>
 */
public class RenderTypes
{
    private RenderTypes()
    {
        throw new IllegalStateException();
    }

    /**
     * Usable for rendering simple flat textures (citizen status icons under the name tag).
     *
     * <p>PORT26 FIX v5: delegates to the vanilla see-through text type — a VANILLA
     * pipeline ({@code minecraft:pipeline/text_see_through}) with per-texture sampler,
     * translucent blending and no depth testing (the old always-pass/no-write contract).
     * Iris redirects it into the pack's text program like every name tag, instead of
     * warning about an unknown modded pipeline and drawing it outside the pack's frame.</p>
     *
     * @param resLoc texture location.
     * @return the render type.
     */
    public static RenderType worldEntityIcon(final Identifier resLoc)
    {
        return net.minecraft.client.renderer.rendertype.RenderTypes.textSeeThrough(resLoc);
    }
}
