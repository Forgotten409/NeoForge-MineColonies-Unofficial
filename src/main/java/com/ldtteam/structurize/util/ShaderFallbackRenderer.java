package com.ldtteam.structurize.util;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;

import java.util.SequencedMap;

/**
 * PORT26-compat (Iris &amp; shader packs): owns the fallback render buffers used
 * while a shader pack is active.
 *
 * <p>Problem being solved: with Iris + an active pack, structurize's custom
 * {@code RenderPipeline}s ({@code structurize:pipeline/lines_with_width} etc.)
 * are not part of any pack's program override list — Iris logs
 * {@code "Missing program ... in override list"} and the draws are erased by the
 * pack's composite (invisible overlays, and the blueprint preview's manual
 * render pass can crash mid-frame).</p>
 *
 * <p>While a pack is active, all overlay geometry is therefore emitted into
 * <b>this</b> owned {@link MultiBufferSource.BufferSource} (with fixed buffers,
 * so vanilla's global {@code endBatch()} can never flush it early) using
 * render types built on <b>vanilla</b> pipelines only (Iris redirects vanilla
 * pipelines into the pack's programs, so they composite correctly, exactly like
 * vanilla's own debug lines and piston blocks).</p>
 *
 * <p>PORT26 FIX v3 (preview z-fighting under packs — "blocks flicker when the
 * building is placed into the ground"): the moving-block fallback types are no
 * longer the vanilla instances — which have no depth bias, so preview faces
 * coplanar with world terrain flipped the LEQUAL depth test frame-to-frame.
 * They are now byte-for-byte copies of the vanilla moving-block setups
 * (identical vanilla pipeline objects, so Iris still redirects them into the
 * pack's programs) with {@link LayeringTransform#VIEW_OFFSET_Z_LAYERING} added —
 * the vanilla technique for coplanar layered geometry (armor, item entities,
 * debug boxes): a uniform {@code 1 - 1/4096} scale of the model-view matrix that
 * pulls the geometry toward the camera by a depth-space bias. Deterministic,
 * distance-proportional, invisible — the preview now consistently wins the
 * depth test against the terrain it is placed in, matching the polygon-offset
 * bias the non-shader preview pipelines use (see
 * {@code BlueprintRenderer.BlueprintRenderTypes}).</p>
 *
 * <p>PORT26 FIX v2 (floating overlays + black screen fragments): the first
 * iteration deferred the flush to {@code RenderLevelStageEvent.AfterLevel} and
 * re-applied the camera rotation on the global model-view stack. Both were
 * wrong: {@code LevelRenderer#renderLevel} keeps the level view matrix pushed
 * on that stack for the <b>whole</b> frame-graph execution, so the overlays were
 * rotated twice (the placement line box orbited around wherever the player
 * looked), and drawing after the graph's declared passes corrupted parts of the
 * main target (black fragments when aiming at a placement spot). The flush now
 * runs mid-frame, at the regular stage points, pinned to exactly the model-view
 * matrix the event carries — {@link #flush} is called at the end of
 * {@code WorldRenderMacros#renderWorldLastEvent} (covers every context that
 * renders through the shared base class) and once more from
 * {@code WorldRenderMacros.RenderTypes#finishBuffer} as a safety net.</p>
 */
public final class ShaderFallbackRenderer
{
    private static MultiBufferSource.BufferSource fallbackBuffers;

    private static final RenderType LINES_TYPE       = RenderTypes.lines();

    /** @see #solidSetup() */
    private static final RenderSetup SOLID_SETUP       = createMovingBlockSetup(
      RenderPipelines.SOLID_BLOCK, false);
    /** @see #cutoutSetup() */
    private static final RenderSetup CUTOUT_SETUP      = createMovingBlockSetup(
      RenderPipelines.CUTOUT_BLOCK, false);
    /** @see #translucentSetup() */
    private static final RenderSetup TRANSLUCENT_SETUP = createMovingBlockSetup(
      RenderPipelines.TRANSLUCENT_BLOCK, true);

    private static final RenderType SOLID_TYPE       = RenderType.create("structurize_shader_fallback_solid", SOLID_SETUP);
    private static final RenderType CUTOUT_TYPE      = RenderType.create("structurize_shader_fallback_cutout", CUTOUT_SETUP);
    private static final RenderType TRANSLUCENT_TYPE = RenderType.create("structurize_shader_fallback_translucent", TRANSLUCENT_SETUP);

    private ShaderFallbackRenderer()
    {
        throw new IllegalStateException("Utility class");
    }

    /**
     * A copy of vanilla's {@code RenderTypes#createMovingBlockSetup} for the given
     * (vanilla!) block pipeline, plus the coplanar-layering view offset — see the
     * class docs for why the depth bias is needed under shader packs.
     *
     * <p>The pipeline must stay one of the vanilla block pipelines
     * ({@code SOLID_BLOCK}/{@code CUTOUT_BLOCK}/{@code TRANSLUCENT_BLOCK}): Iris
     * redirects pipelines by identity/location into the pack's programs, so a
     * custom pipeline here would drop out of the pack's frame integration again
     * (invisible draws). The layering transform is a {@link RenderSetup} property
     * applied CPU-side on the model-view stack inside {@code RenderType#draw},
     * so it does not affect program redirection at all.</p>
     *
     * <p>PORT26 FIX v4 (preview FPS under packs): the setup objects are kept as
     * fields (not only wrapped into {@link RenderType}s) so the GPU-mesh preview
     * pass in {@code BlueprintRenderer#drawOpaqueMeshes} can reuse the exact
     * texture bindings ({@code RenderSetup#getTextures()}) — drawing persistent
     * GPU buffers through the same vanilla pipelines and samplers that the CPU
     * fallback types use, instead of re-emitting every quad every frame.</p>
     *
     * @param pipeline   a vanilla block pipeline.
     * @param translucent translucency flag (sorts on upload, item-entity target).
     * @return the biased moving-block render setup.
     */
    private static RenderSetup createMovingBlockSetup(final RenderPipeline pipeline, final boolean translucent)
    {
        final RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(pipeline)
            .useLightmap()
            .withTexture(
                "Sampler0",
                TextureAtlas.LOCATION_BLOCKS,
                () -> RenderSystem.getSamplerCache()
                    .getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, true))
            .affectsCrumbling()
            .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING);
        if (translucent)
        {
            setup.sortOnUpload();
            setup.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET);
        }
        return setup.createRenderSetup();
    }

    /**
     * The owned buffer source for the shader-pack fallback path. Buffers are
     * registered as <em>fixed</em> so switching between the fallback types
     * never implicitly flushes (draws) them — they stay open until
     * {@link #flush(RenderLevelStageEvent)}.
     *
     * @return the shared, lazily created fallback buffer source.
     */
    public static MultiBufferSource.BufferSource buffers()
    {
        if (fallbackBuffers == null)
        {
            final SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers = new Object2ObjectLinkedOpenHashMap<>();
            put(fixedBuffers, LINES_TYPE);
            put(fixedBuffers, SOLID_TYPE);
            put(fixedBuffers, CUTOUT_TYPE);
            put(fixedBuffers, TRANSLUCENT_TYPE);
            fallbackBuffers = MultiBufferSource.immediateWithBuffers(fixedBuffers, new ByteBufferBuilder(256 * 1024));
        }
        return fallbackBuffers;
    }

    private static void put(final SequencedMap<RenderType, ByteBufferBuilder> map, final RenderType type)
    {
        map.put(type, new ByteBufferBuilder(type.bufferSize()));
    }

    /**
     * Fallback types (see class docs). The line type's vertex format
     * ({@code POSITION_COLOR_NORMAL_LINE_WIDTH}) is written through
     * {@link LineSegmentAdapter}; the moving-block types share the standard
     * block vertex format with our preview emission code — they are the biased
     * copies of the vanilla moving-block types, NOT the vanilla instances.
     */
    public static RenderType linesType()
    {
        return LINES_TYPE;
    }

    public static RenderType solidType()
    {
        return SOLID_TYPE;
    }

    public static RenderType cutoutType()
    {
        return CUTOUT_TYPE;
    }

    public static RenderType translucentType()
    {
        return TRANSLUCENT_TYPE;
    }

    /**
     * PORT26 FIX v4 (preview FPS under packs): the fallback render setups (vanilla
     * block pipelines + block-atlas/lightmap bindings + view-offset layering) —
     * consumed by {@code BlueprintRenderer#drawOpaqueMeshes} to draw the persistent
     * GPU preview meshes under an active pack with the exact same pipeline,
     * texture and layering configuration the CPU fallback types would use.
     */
    public static RenderSetup solidSetup()
    {
        return SOLID_SETUP;
    }

    public static RenderSetup cutoutSetup()
    {
        return CUTOUT_SETUP;
    }

    public static RenderSetup translucentSetup()
    {
        return TRANSLUCENT_SETUP;
    }

    /**
     * Flushes all fallback buffers. Called at the regular render-stage points
     * (end of {@code WorldRenderMacros#renderWorldLastEvent} and the
     * {@code finishBuffer} safety net) — mid-frame, inside the main pass, where
     * every other immediate-mode draw of the frame happens. The model-view
     * matrix is pinned to exactly what the event carries (the level view
     * matrix), the same matrix the no-shader path draws with — see the class
     * docs for why the old AfterLevel + camera-rotation deferral was wrong.
     *
     * <p>Safe to call multiple times per frame: {@code endBatch} consumes the
     * batches, so a flush with nothing new since the last one is a no-op.</p>
     */
    public static void flush(final RenderLevelStageEvent event)
    {
        if (fallbackBuffers == null)
        {
            return;
        }

        final Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        try
        {
            modelViewStack.identity();
            modelViewStack.mul(event.getModelViewMatrix());

            fallbackBuffers.endBatch(SOLID_TYPE);
            fallbackBuffers.endBatch(CUTOUT_TYPE);
            fallbackBuffers.endBatch(LINES_TYPE);
            // translucent last (sorted on upload against the same camera)
            fallbackBuffers.endBatch(TRANSLUCENT_TYPE);
        }
        finally
        {
            modelViewStack.popMatrix();
        }
    }
}
