package com.ldtteam.domumornamentum.client.render;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * PORT26: the old custom RenderType machinery (RenderStateShard subclasses, custom depth tests,
 * measurement/chisel-wireframe types) no longer exists — RenderType moved to
 * {@code net.minecraft.client.renderer.rendertype.RenderType} and is now created from a
 * {@code RenderSetup} bound to a {@code RenderPipeline}. All of the legacy types except the two
 * ghost-preview ones were unused; the ghost previews are mapped onto the vanilla moving-block
 * render types (the same types vanilla uses for piston-moved blocks, which is exactly the
 * "block model rendered outside of chunk sections" use case of the ghost preview).
 */
public enum ModRenderTypes
{
    GHOST_BLOCK_PREVIEW(RenderTypes.translucentMovingBlock()),
    GHOST_BLOCK_COLORED_PREVIEW(RenderTypes.translucentMovingBlock());

    private final RenderType type;

    ModRenderTypes(final RenderType type)
    {
        this.type = type;
    }

    public RenderType get()
    {
        return type;
    }
}
