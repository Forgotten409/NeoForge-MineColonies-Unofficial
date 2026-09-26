package com.minecolonies.core.client.render.worldevent;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.function.Function;

/**
 * Mod render types.
 *
 * <p>PORT26: fully rewritten — the 1.21.1 {@code RenderType.create(name, format, mode, ...)}
 * + {@code CompositeState} shard system is gone in 26.1. Each render type is now
 * {@code RenderType#create(name, RenderSetup)} on top of a registered
 * {@link RenderPipeline} (the same pattern the ported structurize {@code WorldRenderMacros}
 * uses). The pipelines must be registered on the mod bus before first use — see
 * {@link #registerPipelines(RegisterRenderPipelinesEvent)}.</p>
 */
public class RenderTypes
{
    private RenderTypes()
    {
        throw new IllegalStateException();
    }

    /**
     * POSITION_TEX pipeline with translucent blending and an always-pass depth test
     * (the old AlwaysDepthTestStateShard): the icon is drawn regardless of occluders,
     * without writing depth — same visual contract as the 1.21.1 entity icon type.
     */
    private static final RenderPipeline WORLD_ENTITY_ICON_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("minecolonies", "pipeline/world_entity_icon"))
        .withVertexShader("core/position_tex")
        .withFragmentShader("core/position_tex")
        .withSampler("Sampler0")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .build();

    private static final Function<Identifier, RenderType> WORLD_ENTITY_ICON = Util.memoize(texture -> RenderType.create(
        "minecolonies_entity_icon",
        RenderSetup.builder(WORLD_ENTITY_ICON_PIPELINE)
            .withTexture("Sampler0", texture)
            .bufferSize(1024)
            .createRenderSetup()));

    /**
     * Usable for rendering simple flat textures (citizen status icons under the name tag).
     *
     * @param resLoc texture location.
     * @return the render type.
     */
    public static RenderType worldEntityIcon(final Identifier resLoc)
    {
        return WORLD_ENTITY_ICON.apply(resLoc);
    }

    /**
     * PORT26: custom pipelines must be registered (mod bus, client) before use.
     *
     * @param event the pipeline registration event.
     */
    public static void registerPipelines(final RegisterRenderPipelinesEvent event)
    {
        event.registerPipeline(WORLD_ENTITY_ICON_PIPELINE);
    }

    // No RegisterRenderBuffersEvent registration needed: the icon render type is
    // texture-parameterized (memoized per texture) and is batched through the buffer
    // source's shared fallback buffer, exactly like vanilla's memoized texture types.
}
