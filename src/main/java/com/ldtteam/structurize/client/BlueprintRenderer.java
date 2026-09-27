package com.ldtteam.structurize.client;

import com.ldtteam.structurize.Structurize;
import com.ldtteam.structurize.blockentities.BlockEntityTagSubstitution;
import com.ldtteam.structurize.blocks.ModBlocks;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.blueprints.v1.BlueprintUtils;
import com.ldtteam.structurize.client.fakelevel.BlueprintBlockAccess;
import com.ldtteam.structurize.component.CapturedBlock;
import com.ldtteam.structurize.storage.rendering.types.BlueprintPreviewData;
import com.ldtteam.structurize.tag.ModTags;
import com.ldtteam.structurize.util.BlockInfo;
import com.ldtteam.structurize.util.ShaderFallbackRenderer;
import com.ldtteam.structurize.util.ShaderPackCompat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import java.util.function.Consumer;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.model.data.ModelData;
import org.joml.Vector3fc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The renderer for blueprint.
 * Holds all information required to render a blueprint.
 *
 * <p>PORT26: full rewrite of the 1.21.1 immediate-mode renderer. The old class baked block
 * geometry into GL {@code VertexBuffer}s and drew them with raw shader state
 * ({@code RenderType.setupRenderState}/{@code RenderSystem.getShader}), which no longer exists.
 * The 26.1 architecture:
 * <ul>
 *   <li><b>Bake</b> (once per blueprint): every block is tessellated through
 *       {@link ModelBlockRenderer#tesselateBlock} into {@link BakedQuad}s (with AO + tint
 *       resolved into {@link QuadInstance}), cached per {@link ChunkSectionLayer}. The quads
 *       are immutable records, so they can be replayed any number of times.</li>
 *   <li><b>Draw</b> (every frame, from {@code RenderLevelStageEvent}): the cached quads are
 *       replayed into {@code bufferSource} using depth-biased copies of the vanilla
 *       moving-block render types (see {@link BlueprintRenderTypes} — block atlas bound,
 *       core/block shader, DynamicTransforms matrices), or the custom ghost render type when
 *       preview transparency is enabled (alpha is applied per-vertex during the replay,
 *       replacing the old OpenGL blend-color {@code TransparencyHack}).</li>
 *   <li><b>Entities + block entities</b> are no longer drawn here — 26.1 renders them from
 *       render states, so {@link #extractInto} appends {@link EntityRenderState}s and
 *       {@link BlockEntityRenderState}s to the level render state (see
 *       {@code BlueprintHandler#extractRenderStates} on {@code ExtractLevelRenderStateEvent})
 *       and vanilla submits them with correct lighting/fog/pipelines.</li>
 *   <li><b>Fluids</b> (scanned liquids, waterlogged blocks, fluid substitution placeholders)
 *       are tessellated through {@link FluidRenderer#tesselate} exactly like vanilla's
 *       {@code SectionCompiler#compile}: the emitted section-local vertices are recorded per
 *       {@link ChunkSectionLayer} ({@link FluidVertexRecorder}), rebased to blueprint-local
 *       space and replayed with the same pose transform as the block quads.</li>
 * </ul>
 */
public class BlueprintRenderer implements AutoCloseable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BlueprintRenderer.class);

    private static boolean hasWarnedExceptions = false;

    private final BlueprintBlockAccess blockAccess;
    private final RenderingView renderingView;

    List<Entity> entities = List.of();
    private List<BlockEntity> tileEntities = List.of();

    /**
     * Baked geometry, per chunk section layer. Null until the first {@link #init}.
     */
    private Map<ChunkSectionLayer, List<CachedBlockQuad>> cachedQuads;

    /**
     * Baked fluid geometry, per chunk section layer. Null until the first {@link #init}.
     */
    private Map<ChunkSectionLayer, List<CachedFluidVertex>> cachedFluidVertices;

    /**
     * PORT26 FIX (preview placeholder blocks): the model data of every instantiated
     * blueprint block entity ({@code BlockEntity#getModelData()}), served to the block
     * tessellator through {@link RenderingView#getModelData(BlockPos)}. Without this, the
     * NeoForge-level default returns {@link ModelData#EMPTY} and every dynamic model
     * (Domum Ornamentum's materially-textured compat blocks — the bulk of the byzantine
     * style — plus any {@code DynamicBlockStateModel}) falls back to its inner placeholder
     * model in previews, making the whole structure render as grey placeholder blocks.
     */
    private Map<BlockPos, ModelData> tileEntityModelData = Map.of();

    /**
     * PORT26 FIX (preview perf): SOLID/CUTOUT geometry baked once into GPU-resident vertex
     * buffers and drawn through a dedicated render pass — the exact pattern of vanilla's
     * {@code ChunkSectionsToRender#renderGroup} (one pass, shared sequential index buffer,
     * per-frame cost = one dynamic-uniform write + two draw calls). This replaces the old
     * per-frame replay that re-emitted every cached quad through the CPU vertex builder
     * with per-vertex matrix math — the thing that dropped large previews to ~10 fps.
     *
     * <p>TRANSLUCENT and the ghost/transparency mode stay on the per-vertex emission path
     * because those render types are {@code sortOnUpload} (camera-distance quad sorting)
     * which a pre-baked buffer draw does not reproduce.</p>
     */
    private Map<ChunkSectionLayer, BakedLayerMesh> gpuOpaqueMeshes;

    /**
     * PORT26 FIX v4 (preview FPS under packs): the pack state the current GPU meshes
     * were baked for. The shader-pack light floor ({@link #PACK_PREVIEW_LIGHT_FLOOR})
     * is baked INTO the mesh vertices, so a pack on/off flip requires a rebake —
     * rare (a settings/reload event), and handled lazily at draw time.
     */
    private Boolean meshesBakedForPack;

    /**
     * A baked preview layer: the GPU-resident vertex buffer (blueprint-local coordinates —
     * the camera-relative translation is supplied per frame through the global model-view
     * stack), the index count for the shared sequential QUADS index buffer, and the vertex
     * format the buffer was baked with (see {@link #opaqueMeshFormatDrift} — under an active
     * shader pack Iris swaps the pipeline's vertex format to its EXTENDED terrain variant
     * while the level is being rendered, and the baked mesh stride must match what the draw
     * pass will actually interpret).
     */
    private record BakedLayerMesh(GpuBuffer vertexBuffer, int indexCount, VertexFormat format)
    {
    }

    private long lastGameTime;
    private Set<Object> crashingObjects = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * PORT26-compat (Iris & shader packs): minimum packed-light level the preview
     * vertices are clamped up to while a pack is active (0 = no clamping).
     *
     * <p>Problem being solved ("parts of the building are pitch black at night with
     * shaders"): the preview light is baked from the fake level's light provider —
     * either world light at the placement position (config {@code light_level = -1})
     * or a fixed configured level. With a realistic-darkness pack (Complementary &
     * friends) the areas the lightmap sees as (block 0, sky 0) — interior blocks,
     * everything inside the terrain the building is sunk into — render truly black,
     * and the pack's shadow darkening suppresses the sky contribution of anything in
     * the moon's shadow the same way. The floor clamps both channels to a
     * shadow-immune "clearly visible preview" level (block light 8 ≈ nearby torch):
     * exactly like the vanilla minimum ambient that made those areas merely dark
     * instead of black. Only applied under an active pack — without shaders the
     * vanilla lightmap's own ambient floor already keeps the preview visible, and
     * the no-pack rendering stays pixel-identical to the 1.21.1 behaviour.</p>
     */
    private static final int PACK_PREVIEW_LIGHT_FLOOR = 8;

    /**
     * A single baked block quad: the quad itself (immutable record), the AO/tint/light data
     * captured in a private {@link QuadInstance} copy, and the block-local offset the
     * tessellator passed for the quad.
     */
    private record CachedBlockQuad(float x, float y, float z, BakedQuad quad, QuadInstance instance)
    {
    }

    /**
     * A single baked fluid vertex, as emitted by {@link FluidRenderer} (position rebased to
     * blueprint-local space by the recorder, packed color/uv/overlay/light and the normal).
     */
    private record CachedFluidVertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
      float nx, float ny, float nz)
    {
    }

    /**
     * Static factory utility method to handle the extraction of the values from the blueprint.
     *
     * @param blueprint The blueprint to create an instance for.
     * @return The renderer.
     */
    public static BlueprintRenderer buildRendererForBlueprint(final Blueprint blueprint)
    {
        final BlueprintBlockAccess blockAccess = new BlueprintBlockAccess(blueprint);
        return new BlueprintRenderer(blockAccess);
    }

    private BlueprintRenderer(final BlueprintBlockAccess blockAccess)
    {
        this.blockAccess = blockAccess;
        this.renderingView = new RenderingView();
    }

    /**
     * Updates blueprint reference if it has same hash.
     *
     * @param previewData blueprint and context from active structure
     */
    public void updateBlueprint(final BlueprintPreviewData previewData)
    {
        if (blockAccess.getLevelSource() != previewData.getBlueprint() && blockAccess.getLevelSource().hashCode() == previewData.getBlueprint().hashCode())
        {
            blockAccess.setLevelSource(previewData.getBlueprint());
        }
    }

    private void init(final BlueprintPreviewData previewData, final Map<Object, Exception> suppressedExceptions)
    {
        final Blueprint blueprint = previewData.getBlueprint();
        final Minecraft mc = Minecraft.getInstance();
        final ModelBlockRenderer blockRenderer = new ModelBlockRenderer(true, true, mc.getBlockColors());
        final BlockStateModelSet modelSet = mc.getModelManager().getBlockStateModelSet();
        final FluidRenderer fluidRenderer = new FluidRenderer(mc.getModelManager().getFluidStateModelSet());
        final Map<ChunkSectionLayer, FluidVertexRecorder> fluidRecorders = new EnumMap<>(ChunkSectionLayer.class);
        // FluidRenderer emits vertices at section-relative coordinates (pos & 15); the shared
        // origin is updated per block so the recorders rebase them into blueprint-local space
        // — the same coordinate space the cached block quads live in.
        final int[] fluidSectionOrigin = new int[3];
        final FluidRenderer.Output fluidOutput = layer -> fluidRecorders.computeIfAbsent(layer, key -> new FluidVertexRecorder(fluidSectionOrigin));

        final Map<BlockPos, BlockEntity> tileEntitiesMap = BlueprintUtils.instantiateTileEntities(blueprint, blockAccess, new HashMap<>());
        entities = BlueprintUtils.instantiateEntities(blueprint, blockAccess);

        // PORT26 FIX (preview placeholder blocks): keep the TE model data so the tessellator
        // can retexture dynamic models (DO compat blocks — the bulk of the byzantine style)
        // during the bake below — served through RenderingView#getModelData. Previously the
        // map was collected by BlueprintUtils.instantiateTileEntities and then dropped on the
        // floor, so every dynamic model rendered its placeholder fallback in previews.
        final Map<BlockPos, ModelData> collectedModelData = new HashMap<>();
        for (final Map.Entry<BlockPos, BlockEntity> entry : tileEntitiesMap.entrySet())
        {
            final ModelData data = entry.getValue().getModelData();
            if (data != null)
            {
                collectedModelData.put(entry.getKey(), data);
            }
        }
        tileEntityModelData = collectedModelData;

        blockAccess.setBlockEntities(tileEntitiesMap);
        blockAccess.setEntities(entities);
        blockAccess.setSolidSubstitutionOverride(previewData.getSolidSubstitutionOverride());
        blockAccess.setRenderBlocksNiceOverride(previewData.getRenderBlocksNice());

        cachedQuads = new EnumMap<>(ChunkSectionLayer.class);

        for (final BlockInfo blockInfo : blueprint.getBlockInfoAsList())
        {
            final BlockPos blockPos = blockInfo.getPos();
            BlockState state = blockInfo.getState();
            // specially handle blockTagSub here cuz of block entity changes
            if (previewData.getRenderBlocksNice() && state.getBlock() == ModBlocks.blockTagSubstitution.get())
            {
                if (tileEntitiesMap.remove(blockPos) instanceof final BlockEntityTagSubstitution tagTE)
                {
                    final CapturedBlock replacement = tagTE.getReplacement();
                    state = replacement.blockState();

                    replacement.serializedBE().map(tag -> BlockEntity.loadStatic(blockPos, replacement.blockState(), tag, blueprint.getRegistryAccess())).ifPresent(newBe -> {
                        newBe.setLevel(blockAccess);
                        tileEntitiesMap.put(blockPos, newBe);
                    });
                }
                else
                {
                    state = Blocks.AIR.defaultBlockState();
                }
            }
            else
            {
                state = blockAccess.prepareBlockStateForRendering(state, blockPos);
            }

            try
            {
                // PORT26: fluids first, block model second — exactly like vanilla's
                // SectionCompiler (scanned liquids, waterlogged blocks and fluid substitution
                // placeholders resolved by the block access all land here)
                final FluidState fluidState = state.getFluidState();
                if (!fluidState.isEmpty())
                {
                    fluidSectionOrigin[0] = blockPos.getX() & ~15;
                    fluidSectionOrigin[1] = blockPos.getY() & ~15;
                    fluidSectionOrigin[2] = blockPos.getZ() & ~15;
                    fluidRenderer.tesselate(renderingView, blockPos, fluidOutput, state, fluidState);
                }

                if (state.getRenderShape() == RenderShape.MODEL)
                {
                    final BlockStateModel model = modelSet.get(state);
                    blockRenderer.tesselateBlock(
                      (x, y, z, quad, instance) -> cachedQuads
                        .computeIfAbsent(quad.materialInfo().layer(), layer -> new ArrayList<>())
                        .add(new CachedBlockQuad(x, y, z, quad, copyQuadInstance(instance))),
                      (float) blockPos.getX(), (float) blockPos.getY(), (float) blockPos.getZ(),
                      renderingView, blockPos, state, model, state.getSeed(blockPos));
                }
            }
            catch (final ReportedException e)
            {
                suppressedExceptions.put(blockInfo, e);
            }
        }

        blockAccess.setSolidSubstitutionOverride(null);
        blockAccess.setRenderBlocksNiceOverride(Structurize.getConfig().getClient().renderPlaceholdersNice.get());

        cachedFluidVertices = new EnumMap<>(ChunkSectionLayer.class);
        for (final Map.Entry<ChunkSectionLayer, FluidVertexRecorder> entry : fluidRecorders.entrySet())
        {
            if (!entry.getValue().vertices().isEmpty())
            {
                cachedFluidVertices.put(entry.getKey(), entry.getValue().vertices());
            }
        }

        tileEntities = new ArrayList<>(tileEntitiesMap.values());

        // PORT26 FIX (preview perf): bake SOLID/CUTOUT into GPU-resident vertex buffers once
        // — the per-frame replay then costs one dynamic-uniform write + two draw calls
        // instead of a CPU vertex emission per quad (the ~10 fps on large previews).
        // PORT26 FIX v4: baked with the CURRENT pack state's light floor (see
        // meshesBakedForPack — a later flip triggers a lazy rebake at draw time).
        // PORT26 FIX v5 (black preview meshes under packs): with a pack ALREADY ACTIVE the
        // bake is deferred to the first draw instead — it must happen INSIDE the level
        // render, where Iris's terrain vertex-format extension is active, so the baked
        // stride matches what the draw pass interprets (see uploadOpaqueLayerMeshes).
        // The stale meshes are released and the lazy-bake flag reset so the draw path
        // always rebakes (a blueprint re-init under a pack must never keep drawing the
        // previous blueprint's buffers).
        if (!ShaderPackCompat.isShaderPackActive())
        {
            uploadOpaqueLayerMeshes();
        }
        else
        {
            closeGpuOpaqueMeshes();
            meshesBakedForPack = null;
        }
    }

    /**
     * Bakes the SOLID and CUTOUT layer geometry (block quads + fluid vertices) into
     * GPU-resident vertex buffers in blueprint-local coordinates (identity pose — the
     * camera-relative translation is supplied at draw time through the global model-view
     * stack, so the buffers stay valid across frames).
     *
     * <p>Only these two layers are baked: they carry the overwhelming majority of the quads
     * and are not {@code sortOnUpload}. TRANSLUCENT (and the whole ghost mode) keep the
     * per-vertex path so camera-distance quad sorting is preserved.</p>
     */
    private void uploadOpaqueLayerMeshes()
    {
        closeGpuOpaqueMeshes();
        final boolean packActive = ShaderPackCompat.isShaderPackActive();
        meshesBakedForPack = packActive;
        // GPU mesh path is drawn under BOTH modes since FIX v4 — under a pack the
        // light floor is baked into the vertices (see PACK_PREVIEW_LIGHT_FLOOR:
        // the floor is a shader-pack mitigation); without a pack it stays 0.
        final int lightFloor = packActive ? PACK_PREVIEW_LIGHT_FLOOR : 0;
        gpuOpaqueMeshes = new EnumMap<>(ChunkSectionLayer.class);
        if (cachedQuads == null)
        {
            return;
        }

        final PoseStack.Pose identityPose = new PoseStack().last();
        for (final ChunkSectionLayer layer : new ChunkSectionLayer[] {ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT})
        {
            final List<CachedBlockQuad> quads = cachedQuads.get(layer);
            final List<CachedFluidVertex> fluidVertices = cachedFluidVertices == null ? null : cachedFluidVertices.get(layer);
            final boolean hasQuads = quads != null && !quads.isEmpty();
            final boolean hasFluids = fluidVertices != null && !fluidVertices.isEmpty();
            if (!hasQuads && !hasFluids)
            {
                continue;
            }

            final RenderType renderType = previewMovingBlockType(layer);
            final int vertexCount = (hasQuads ? quads.size() * 4 : 0) + (hasFluids ? fluidVertices.size() : 0);
            final ByteBufferBuilder byteBuffer = new ByteBufferBuilder(Math.max(1024, vertexCount * renderType.format().getVertexSize()));
            final BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.mode(), renderType.format());
            if (hasQuads)
            {
                for (final CachedBlockQuad cachedQuad : quads)
                {
                    putQuad(builder, identityPose, cachedQuad, -1, lightFloor);
                }
            }
            if (hasFluids)
            {
                for (final CachedFluidVertex fluidVertex : fluidVertices)
                {
                    putFluidVertex(builder, identityPose, fluidVertex, -1, lightFloor);
                }
            }

            final MeshData mesh = builder.build();
            if (mesh != null)
            {
                // Upload once, keep the GPU copy for the lifetime of this renderer
                // (same usage flags as vanilla's immediate vertex buffers: VERTEX | COPY_DST).
                final GpuBuffer vertexBuffer = RenderSystem.getDevice()
                  .createBuffer(() -> "structurize preview " + layer,
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    mesh.vertexBuffer());
                // PORT26 FIX v5 (black preview meshes under packs): remember the vertex format
                // this buffer was built with. While a pack is active, Iris swaps
                // RenderPipeline#getVertexFormat() to its EXTENDED terrain format (extra
                // shader attributes) during the level render — so a mesh baked OUTSIDE that
                // window (plain block stride) does not match what GlCommandEncoder will
                // interpret at draw time (extended stride), and every vertex past the first
                // decodes as garbage: the preview renders as "thousands of black meshes".
                // The draw path compares this stored format against the format the draw
                // pass is about to use (opaqueMeshFormatDrift) and rebakes on mismatch —
                // evaluating BOTH sides at draw time (inside the level render stage) keeps
                // them consistent by construction.
                gpuOpaqueMeshes.put(layer, new BakedLayerMesh(vertexBuffer, mesh.drawState().indexCount(), renderType.format()));
                mesh.close();
            }
        }
    }

    /**
     * Releases the GPU-resident preview layer buffers (renderer re-init / close).
     */
    private void closeGpuOpaqueMeshes()
    {
        if (gpuOpaqueMeshes != null)
        {
            for (final BakedLayerMesh baked : gpuOpaqueMeshes.values())
            {
                baked.vertexBuffer().close();
            }
            gpuOpaqueMeshes = null;
        }
    }

    /**
     * PORT26 FIX v5 (0.4.5, "the whole preview renders black / thousands of black meshes"
     * under an active shader pack): checks whether the baked GPU meshes still match the
     * vertex format the draw pass is about to interpret.
     *
     * <p>While a shader pack renders the level, Iris's mixin on
     * {@code RenderPipeline#getVertexFormat()} answers with its EXTENDED terrain format
     * (extra shader attributes, larger stride) — and {@code GlCommandEncoder#executeDraw}
     * binds vertex attributes through exactly that call. A mesh baked <em>outside</em> the
     * level render window (e.g. during render-state extraction, when the extension is
     * inactive) has the plain block format's stride, so the draw pass misreads every
     * vertex past the first — the preview decodes as garbage ("thousands of black
     * meshes"). Comparing the stored bake-time format against
     * {@code RenderType#pipeline()#getVertexFormat()} <em>here</em> — inside the level
     * render stage, the same flag state the draw below will run under — makes bake and
     * draw consistent by construction: on drift the caller rebakes (which re-reads the
     * format at the same point in the frame, producing the extended-stride mesh), and
     * without a pack (or without Iris) both sides are the plain block format and the
     * check is a stable no-op.</p>
     *
     * @param packMode the current shader-pack state (selects the draw-time render types).
     * @return true when any baked layer's format no longer matches its draw-time format.
     */
    private boolean opaqueMeshFormatDrift(final boolean packMode)
    {
        if (gpuOpaqueMeshes == null)
        {
            return false;
        }
        for (final ChunkSectionLayer layer : new ChunkSectionLayer[] {ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT})
        {
            final BakedLayerMesh baked = gpuOpaqueMeshes.get(layer);
            if (baked == null)
            {
                continue;
            }
            final RenderType type = packMode
                ? (layer == ChunkSectionLayer.SOLID ? ShaderFallbackRenderer.solidType() : ShaderFallbackRenderer.cutoutType())
                : (layer == ChunkSectionLayer.SOLID ? BlueprintRenderTypes.PREVIEW_SOLID : BlueprintRenderTypes.PREVIEW_CUTOUT);
            // reference comparison is exact here: formats are interned instances
            // (DefaultVertexFormat constants / Iris's static extended formats), and both
            // sides are resolved at this same instant of the frame
            if (baked.format() != type.pipeline().getVertexFormat())
            {
                return true;
            }
        }
        return false;
    }

    /**
     * {@link ModelBlockRenderer} reuses one mutable {@link QuadInstance} for every quad it
     * tessellates, so each cached quad needs its own defensive copy.
     */
    private static QuadInstance copyQuadInstance(final QuadInstance source)
    {
        final QuadInstance copy = new QuadInstance();
        for (int vertex = 0; vertex < 4; vertex++)
        {
            copy.setColor(vertex, source.getColor(vertex));
            copy.setLightCoords(vertex, source.getLightCoords(vertex));
        }
        copy.setOverlayCoords(source.overlayCoords());
        return copy;
    }

    /**
     * Draws structure into world.
     */
    public void draw(final BlueprintPreviewData previewData, final BlockPos pos, final RenderLevelStageEvent ctx)
    {
        // we've crashed hard before, full skip
        if (crashingObjects == null)
        {
            return;
        }

        try
        {
            final Map<Object, Exception> suppressedExceptions = drawUnsafe(previewData, pos, ctx);
            if (!suppressedExceptions.isEmpty())
            {
                if (!hasWarnedExceptions)
                {
                    hasWarnedExceptions = true;
                    final LocalPlayer player = Minecraft.getInstance().player;
                    if (player != null)
                    {
                        player.sendSystemMessage(Component.translatable("structurize.preview_renderer.exception"));
                    }
                }

                boolean crashReported = false;
                boolean isEmpty = true;
                for (final Map.Entry<Object, Exception> e : suppressedExceptions.entrySet())
                {
                    if (!crashingObjects.add(e.getKey()))
                    {
                        continue;
                    }
                    isEmpty = false;

                    if (e.getValue() instanceof final ReportedException reportedException)
                    {
                        printCrashReport(reportedException.getReport(), previewData);
                        crashReported = true;
                    }
                    else
                    {
                        LOGGER.error("", e.getValue());
                    }
                }

                if (!crashReported && !isEmpty)
                {
                    printCrashReport(CrashReport.forThrowable(new Exception(), "Small exception, rendering partially"), previewData);
                }
            }
        }
        catch (final Exception e)
        {
            printCrashReport(CrashReport.forThrowable(e, "Fatal exception, cannot render"), previewData);

            crashingObjects = null;
            final LocalPlayer player = Minecraft.getInstance().player;
            if (player != null)
            {
                player.sendSystemMessage(
                  Component.translatable("structurize.preview_renderer.cannot_render", previewData.getBlueprint().getName()));
            }
        }
    }

    private static void printCrashReport(final CrashReport report, final BlueprintPreviewData previewData)
    {
        previewData.getBlueprint().describeSelfInCrashReport(report.addCategory("Blueprint"));
        // PORT26 FIX (Iris-compat debugging): CrashReport does not override toString —
        // logging the object itself printed "net.minecraft.CrashReport@1a2b3c" and the
        // actual crash cause was lost (unusable for bug reports). getFriendlyReport
        // produces the full readable report.
        LOGGER.error("Problem during blueprint rendering:\n{}", report.getFriendlyReport(net.minecraft.ReportType.CRASH));
    }

    /**
     * Draws structure into world (block geometry only — entities and block entities are
     * submitted through their render states, see {@link #extractInto}).
     *
     * @return suppressed exceptions
     */
    public Map<Object, Exception> drawUnsafe(final BlueprintPreviewData previewData, final BlockPos pos, final RenderLevelStageEvent ctx)
    {
        final Blueprint blueprint = previewData.getBlueprint();
        final BlockPos anchorPos = pos.subtract(blueprint.getPrimaryBlockOffset());

        // cull entire rendering
        if (!ctx.getLevelRenderState().cameraRenderState.cullFrustum.isVisible(
          new net.minecraft.world.phys.AABB(0, 0, 0, blueprint.getSizeX(), blueprint.getSizeY(), blueprint.getSizeZ()).move(anchorPos)))
        {
            return Map.of();
        }

        final Map<Object, Exception> suppressedExceptions = new IdentityHashMap<>();
        final Minecraft mc = Minecraft.getInstance();

        // PORT26 FIX (client crash "Pose stack not empty"): any exception between the pose
        // push and its pop used to leak the push on the level renderer's pose stack —
        // LevelRenderer#checkPoseStack then threw IllegalStateException at the end of the
        // very same frame and took the whole game down with an "Unreported exception
        // thrown!" crash (reported with the libraryalt1/tavern1 previews). The profiler
        // section and the pose push are now closed in finally blocks, so a broken
        // blueprint degrades to the blacklist + chat message path in draw() instead of
        // crashing the client.
        Profiler.get().push("struct_render_init");
        try
        {
            // make sure instances are synced
            updateBlueprint(previewData);
            blockAccess.setWorldPos(anchorPos);

            // init
            if (cachedQuads == null)
            {
                init(previewData, suppressedExceptions);
            }

            Profiler.get().popPush("struct_render_blocks");

            final float alpha = effectiveAlpha(previewData);
            final boolean ghost = alpha > 0.0f && alpha < TransparencyHack.THRESHOLD;
            final int alphaByte = ghost ? Mth.ceil(alpha * 255.0f) : -1;

            final Vec3 cameraPos = ctx.getLevelRenderState().cameraRenderState.pos;
            final PoseStack poseStack = ctx.getPoseStack();
            final MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

            poseStack.pushPose();
            try
            {
                drawBlockGeometry(poseStack, bufferSource, anchorPos, cameraPos, ghost, alphaByte, mc);
            }
            finally
            {
                poseStack.popPose();
            }

            Profiler.get().popPush("struct_render_blocks_finish");
            // PORT26-compat (Iris & shader packs): under an active pack the preview geometry
            // lives in the fallback buffers (vanilla moving-block types) and is flushed by
            // WorldRenderMacros#renderWorldLastEvent right after this context finished —
            // mid-frame, through the pack-supported vanilla types, with the exact level
            // model-view matrix the event carries. NOT here (the old AfterLevel deferral
            // drew outside the frame graph's passes and double-rotated the overlays —
            // see ShaderFallbackRenderer).
            if (!ShaderPackCompat.isShaderPackActive())
            {
                if (ghost)
                {
                    bufferSource.endBatch(BlueprintRenderTypes.BLUEPRINT_GHOST);
                }
                else
                {
                    // draw order matters: solid, cutout, translucent (sorted on upload)
                    bufferSource.endBatch(previewMovingBlockType(ChunkSectionLayer.SOLID));
                    bufferSource.endBatch(previewMovingBlockType(ChunkSectionLayer.CUTOUT));
                    bufferSource.endBatch(previewMovingBlockType(ChunkSectionLayer.TRANSLUCENT));
                }
            }
        }
        finally
        {
            Profiler.get().pop();
        }

        return suppressedExceptions;
    }

    /**
     * Emits the baked block/fluid geometry of this blueprint — everything between the pose
     * push and its pop, extracted from {@link #drawUnsafe} so the push/pop pair stays
     * balanced in a finally even when a render exception escapes mid-draw.
     *
     * @param poseStack    the level renderer's pose stack, one pose pushed for the blueprint.
     * @param bufferSource the immediate buffer source to emit through.
     * @param anchorPos    the blueprint anchor in the world (primary offset subtracted).
     * @param cameraPos    the camera position in world space.
     * @param ghost        true → ghost/transparency mode (per-vertex alpha).
     * @param alphaByte    the ghost alpha byte (0-255), or -1 for fully opaque.
     * @param mc           the client.
     */
    private void drawBlockGeometry(final PoseStack poseStack, final MultiBufferSource.BufferSource bufferSource,
        final BlockPos anchorPos, final Vec3 cameraPos, final boolean ghost, final int alphaByte, final Minecraft mc)
    {
        poseStack.translate(anchorPos.getX() - cameraPos.x(), anchorPos.getY() - cameraPos.y(), anchorPos.getZ() - cameraPos.z());
        // PORT26 FIX (batch 24): the pose MUST be baked into the replayed vertices — raw
        // addVertex(x, y, z) applies no matrix, and RenderType#draw resolves its matrix from
        // RenderSystem.getModelViewMatrix() (the level pass camera rotation). Without baking,
        // the blueprint-local coordinates rendered camera-relative: the whole preview was
        // glued to the view, giant, rotating with the camera. Baking (anchorPos - cameraPos)
        // + block-local offsets through the pose puts the preview at its world position,
        // exactly like vanilla's moving-block rendering.
        final PoseStack.Pose pose = poseStack.last();
        // PORT26-compat (Iris & shader packs): light floor for every preview vertex while
        // a pack is active — see PACK_PREVIEW_LIGHT_FLOOR. Computed once per frame here
        // (the reflection probe is cached, but this is a per-quad hot path).
        final int lightFloor = ShaderPackCompat.isShaderPackActive() ? PACK_PREVIEW_LIGHT_FLOOR : 0;

        if (ghost)
        {
            final VertexConsumer consumer = previewBuffer(ghostType(), bufferSource);
            for (final List<CachedBlockQuad> quads : cachedQuads.values())
            {
                for (final CachedBlockQuad cachedQuad : quads)
                {
                    putQuad(consumer, pose, cachedQuad, alphaByte, lightFloor);
                }
            }
            // fluids share the ghost buffer (alpha applied per-vertex like the block quads)
            if (cachedFluidVertices != null)
            {
                for (final List<CachedFluidVertex> fluidVertices : cachedFluidVertices.values())
                {
                    for (final CachedFluidVertex fluidVertex : fluidVertices)
                    {
                        putFluidVertex(consumer, pose, fluidVertex, alphaByte, lightFloor);
                    }
                }
            }
        }
        else
        {
            // PORT26 FIX (preview perf): SOLID/CUTOUT go through the GPU-resident baked
            // buffers (drawn BEFORE the translucent batch — same draw order as before:
            // solid, cutout, translucent). TRANSLUCENT stays on the per-vertex emission
            // path because its render type is sortOnUpload (camera-distance quad sorting).
            //
            // PORT26 FIX v4 (preview FPS under packs): the GPU-mesh pass is NO LONGER
            // skipped under an active shader pack — it now draws through the FALLBACK
            // setups (vanilla SOLID_BLOCK/CUTOUT_BLOCK pipelines — Iris redirects them
            // into the pack's programs exactly like every other immediate draw, plus
            // the same view-offset layering the CPU fallback types apply), see
            // drawOpaqueMeshes. The old pack path re-emitted EVERY quad of the blueprint
            // through the CPU fallback buffers every frame (putQuad per quad, translucent
            // re-upload + sort) — the reported ~10 fps on large building previews under
            // shaders (vs ~100 fps without).
            final boolean shaderFallback = ShaderPackCompat.isShaderPackActive();
            // lazy rebake when the pack state flipped since the meshes were baked
            // (the light floor is baked into the mesh vertices), or when the vertex format
            // the draw pass is about to interpret drifted from the format the meshes were
            // baked with (Iris's pack-time terrain format extension — see
            // opaqueMeshFormatDrift; both sides are evaluated HERE, inside the level render
            // stage, so bake and draw can never disagree)
            if (meshesBakedForPack == null || meshesBakedForPack != shaderFallback || opaqueMeshFormatDrift(shaderFallback))
            {
                uploadOpaqueLayerMeshes();
            }
            drawOpaqueMeshes(pose, mc);

            final List<CachedBlockQuad> translucentQuads = cachedQuads.get(ChunkSectionLayer.TRANSLUCENT);
            final List<CachedFluidVertex> translucentFluids = cachedFluidVertices == null ? null : cachedFluidVertices.get(ChunkSectionLayer.TRANSLUCENT);
            final boolean hasTranslucent = (translucentQuads != null && !translucentQuads.isEmpty())
                                             || (translucentFluids != null && !translucentFluids.isEmpty());
            final boolean solidDrawnViaMesh = gpuOpaqueMeshes != null && gpuOpaqueMeshes.containsKey(ChunkSectionLayer.SOLID);
            final boolean cutoutDrawnViaMesh = gpuOpaqueMeshes != null && gpuOpaqueMeshes.containsKey(ChunkSectionLayer.CUTOUT);
            if (hasTranslucent || !solidDrawnViaMesh || !cutoutDrawnViaMesh)
            {
                final VertexConsumer consumer = previewBuffer(previewMovingBlockType(ChunkSectionLayer.TRANSLUCENT), bufferSource);
                if (translucentQuads != null && !translucentQuads.isEmpty())
                {
                    for (final CachedBlockQuad cachedQuad : translucentQuads)
                    {
                        putQuad(consumer, pose, cachedQuad, -1, lightFloor);
                    }
                }
                if (translucentFluids != null && !translucentFluids.isEmpty())
                {
                    for (final CachedFluidVertex fluidVertex : translucentFluids)
                    {
                        putFluidVertex(consumer, pose, fluidVertex, -1, lightFloor);
                    }
                }

                // fallback for layers whose baked buffer could not be created (e.g. GPU
                // buffer allocation failed) — emit them through the CPU path like before
                for (final ChunkSectionLayer layer : new ChunkSectionLayer[] {ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT})
                {
                    final boolean drawnViaMesh = layer == ChunkSectionLayer.SOLID ? solidDrawnViaMesh : cutoutDrawnViaMesh;
                    if (drawnViaMesh)
                    {
                        continue;
                    }
                    final List<CachedBlockQuad> quads = cachedQuads.get(layer);
                    final List<CachedFluidVertex> fluidVertices = cachedFluidVertices == null ? null : cachedFluidVertices.get(layer);
                    if ((quads == null || quads.isEmpty()) && (fluidVertices == null || fluidVertices.isEmpty()))
                    {
                        continue;
                    }
                    final VertexConsumer layerConsumer = previewBuffer(previewMovingBlockType(layer), bufferSource);
                    if (quads != null)
                    {
                        for (final CachedBlockQuad cachedQuad : quads)
                        {
                            putQuad(layerConsumer, pose, cachedQuad, -1, lightFloor);
                        }
                    }
                    if (fluidVertices != null)
                    {
                        for (final CachedFluidVertex fluidVertex : fluidVertices)
                        {
                            putFluidVertex(layerConsumer, pose, fluidVertex, -1, lightFloor);
                        }
                    }
                }
            }
        }
    }

    /**
     * Draws the baked SOLID/CUTOUT layers through a dedicated render pass — the pattern of
     * vanilla's {@code ChunkSectionsToRender#renderGroup} and {@code RenderType#draw}:
     * one pass, the render setup's own texture bindings (block atlas + lightmap), the
     * shared sequential QUADS index buffer, and one dynamic-uniform write carrying the
     * model-view matrix.
     *
     * <p>PORT26 FIX (preview perf): the vertex buffers were uploaded once at init in
     * blueprint-local coordinates; the camera-relative translation is supplied per frame by
     * pushing the pose onto the global model-view stack ({@code RenderSystem.getModelViewStack()})
     * — mathematically identical to the previous per-vertex pose baking
     * ({@code M * pose * v == (M * pose) * v}), but with zero per-vertex CPU work.</p>
     *
     * <p>PORT26 FIX v4 (preview FPS under packs): under an active pack this pass now draws
     * through the ShaderFallbackRenderer setups — the VANILLA SOLID_BLOCK/CUTOUT_BLOCK
     * pipelines with the block-atlas/lightmap samplers — instead of the custom preview
     * pipelines (which Iris cannot redirect: "Missing program structurize:pipeline/…" →
     * invisible). Iris redirects the vanilla pipelines by identity/location into the pack's
     * programs for EVERY pass, including manually-created ones — the exact same mechanism
     * every {@code RenderType#draw} immediate draw already relies on under packs (piston
     * moving blocks, debug boxes). The view-offset layering ({@code VIEW_OFFSET_Z_LAYERING})
     * — the coplanar depth-bias z-fighting fix — is applied on the model-view stack exactly
     * like {@code RenderType#draw} applies it for the CPU fallback types, so the fast mesh
     * path keeps the same depth-bias behavior. The earlier "skip under pack" behaviour
     * forced every quad back through the per-frame CPU emission (putQuad × every quad,
     * buffer re-upload + translucent sort every frame) — the ~10 fps on large previews.</p>
     *
     * @param pose the camera-relative pose (anchor - camera) previously baked into vertices.
     * @param mc   the client.
     */
    private void drawOpaqueMeshes(final PoseStack.Pose pose, final Minecraft mc)
    {
        if (gpuOpaqueMeshes == null || gpuOpaqueMeshes.isEmpty())
        {
            return;
        }

        final boolean packMode = ShaderPackCompat.isShaderPackActive();

        final Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        try
        {
            // pack mode: the same view-offset layering the CPU fallback types apply inside
            // RenderType#draw (coplanar depth bias — keeps the z-fighting fix on this path).
            //
            // PORT26 FIX 0.5.1 ("grid-like flicker of the structure preview under shader
            // packs"): the modifier MUST be pushed BEFORE the blueprint translation — the
            // exact position RenderType#draw pushes it at. Vanilla's CPU path bakes
            // (anchor - camera) into the vertices and draws with M = V * S, so the S scale
            // acts on CAMERA-RELATIVE coordinates: a uniform pull toward the eye = the
            // intended deterministic depth bias at any distance. This GPU path keeps
            // blueprint-LOCAL vertices with T on the stack, so pushing S after T (the old
            // order) scaled the BLUEPRINT-LOCAL coordinates instead — a pull toward the
            // blueprint's anchor corner: preview blocks near the anchor kept a ~zero depth
            // bias and z-fought against coplanar world faces under packs (the reported
            // flicker; the no-pack path never showed it because its custom pipelines bias
            // with GPU polygon offset). Pushing S first yields M = V * S * T — applied to
            // blueprint-local vertices that is matrix-identical to the CPU path everywhere.
            final Consumer<Matrix4fStack> layering = packMode ? LayeringTransform.VIEW_OFFSET_Z_LAYERING.getModifier() : null;
            if (layering != null)
            {
                modelViewStack.pushMatrix();
                layering.accept(modelViewStack);
            }
            modelViewStack.mul(pose.pose());
            try
            {
                final RenderTarget renderTarget = mc.getMainRenderTarget();
                final GpuTextureView colorTexture = RenderSystem.outputColorTextureOverride != null
                                                       ? RenderSystem.outputColorTextureOverride
                                                       : renderTarget.getColorTextureView();
                final GpuTextureView depthTexture = renderTarget.useDepth
                                                       ? (RenderSystem.outputDepthTextureOverride != null
                                                            ? RenderSystem.outputDepthTextureOverride
                                                            : renderTarget.getDepthTextureView())
                                                       : null;

                // PORT26 FIX (blueprint crash "Close the existing render pass before performing
                // additional commands"): DynamicUniforms#writeTransform maps the uniform ring
                // buffer through CommandEncoder#mapBuffer, which is ILLEGAL while a render pass
                // is open — vanilla's RenderType#draw writes the transform strictly BEFORE
                // createRenderPass (see the decompiled 26.1.2 RenderType#draw). The old order
                // (writeTransform inside the open pass) crashed the first frame the GPU-baked
                // layers drew ("Problem during blueprint rendering: ... IllegalStateException"
                // in CommandEncoder#mapBuffer at drawOpaqueMeshes), killed the placement preview
                // and with it the ability to place buildings.
                final GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                  RenderSystem.getModelViewMatrix(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());

                final RenderPass renderPass = RenderSystem.getDevice()
                  .createCommandEncoder()
                  .createRenderPass(() -> "Structurize blueprint preview (opaque layers)",
                    colorTexture, OptionalInt.empty(), depthTexture, OptionalDouble.empty());
                try
                {
                    RenderSystem.bindDefaultUniforms(renderPass);

                    final RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                    for (final ChunkSectionLayer layer : new ChunkSectionLayer[] {ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT})
                    {
                        final BakedLayerMesh baked = gpuOpaqueMeshes.get(layer);
                        if (baked == null)
                        {
                            continue;
                        }

                        // pack mode → vanilla fallback setups (Iris-redirectable); otherwise
                        // the custom (depth-biased) preview setups — texture bindings come from
                        // the setup object, the pipeline through the RenderType's public accessor
                        // (RenderSetup#pipeline is package-private, RenderType#pipeline() is not)
                        final RenderSetup setup = packMode
                            ? (layer == ChunkSectionLayer.SOLID ? ShaderFallbackRenderer.solidSetup() : ShaderFallbackRenderer.cutoutSetup())
                            : (layer == ChunkSectionLayer.SOLID ? BlueprintRenderTypes.PREVIEW_SOLID_SETUP : BlueprintRenderTypes.PREVIEW_CUTOUT_SETUP);
                        final RenderType type = packMode
                            ? (layer == ChunkSectionLayer.SOLID ? ShaderFallbackRenderer.solidType() : ShaderFallbackRenderer.cutoutType())
                            : (layer == ChunkSectionLayer.SOLID ? BlueprintRenderTypes.PREVIEW_SOLID : BlueprintRenderTypes.PREVIEW_CUTOUT);

                        renderPass.setPipeline(type.pipeline());
                        renderPass.setUniform("DynamicTransforms", dynamicTransforms);
                        renderPass.setVertexBuffer(0, baked.vertexBuffer());
                        // texture bindings exactly as the render setup declares them
                        // (Sampler0 = block atlas, Sampler2 = lightmap from useLightmap())
                        for (final Map.Entry<String, RenderSetup.TextureAndSampler> entry : setup.getTextures().entrySet())
                        {
                            renderPass.bindTexture(entry.getKey(), entry.getValue().textureView(), entry.getValue().sampler());
                        }
                        final GpuBuffer indices = autoIndices.getBuffer(baked.indexCount());
                        renderPass.setIndexBuffer(indices, autoIndices.type());
                        renderPass.drawIndexed(0, 0, baked.indexCount(), 1);
                    }
                }
                finally
                {
                    renderPass.close();
                }
            }
            finally
            {
                if (layering != null)
                {
                    modelViewStack.popMatrix();
                }
            }
        }
        finally
        {
            modelViewStack.popMatrix();
        }
    }

    /**
     * Extracts blueprint entities and block entities as render states onto the level render
     * state; vanilla then submits them in the correct render stage with proper
     * lighting/fog/pipelines. Called from {@code ExtractLevelRenderStateEvent} via
     * {@code BlueprintHandler#extractRenderStates}.
     */
    public void extractInto(final BlueprintPreviewData previewData, final LevelRenderState renderState, final Camera camera, final DeltaTracker deltaTracker)
    {
        if (crashingObjects == null)
        {
            return;
        }
        final Blueprint blueprint = previewData.getBlueprint();
        if (blueprint == null || previewData.getPos() == null)
        {
            return;
        }

        final Minecraft mc = Minecraft.getInstance();
        final BlockPos anchorPos = previewData.getPos().subtract(blueprint.getPrimaryBlockOffset());

        updateBlueprint(previewData);
        blockAccess.setWorldPos(anchorPos);

        if (cachedQuads == null)
        {
            init(previewData, new IdentityHashMap<>());
        }

        final float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(!mc.level.tickRateManager().isFrozen());
        final boolean shouldTick = renderState.gameTime != lastGameTime;

        // entities (positions are world-space on the state)
        mc.getEntityRenderDispatcher().prepare(camera, mc.crosshairPickEntity);
        for (final Entity entity : entities)
        {
            // PORT26: EntityType#is(TagKey) was removed; the tag check lives on the
            // registry Holder now (Holder.Reference#is(TagKey)).
            if (shouldTick && entity.getType().builtInRegistryHolder().is(ModTags.PREVIEW_TICKING_ENTITIES))
            {
                try
                {
                    entity.tick();
                }
                catch (final Exception e)
                {
                    // well, noop
                }
            }

            try
            {
                final EntityRenderState state = mc.getEntityRenderDispatcher().extractEntity(entity, partialTicks);
                if (state != null)
                {
                    state.x += anchorPos.getX();
                    state.y += anchorPos.getY();
                    state.z += anchorPos.getZ();
                    renderState.entityRenderStates.add(state);
                }
            }
            catch (final Exception e)
            {
                if (crashingObjects.add(entity))
                {
                    LOGGER.error("Failed to extract blueprint entity " + entity, e);
                }
            }
        }

        // block entities — prepare the dispatcher with the camera position relative to the
        // blueprint origin so the local-coord distance checks pass
        final Vec3 cameraPos = camera.position();
        mc.getBlockEntityRenderDispatcher().prepare(cameraPos.subtract(anchorPos.getX(), anchorPos.getY(), anchorPos.getZ()));
        for (final BlockEntity tileEntity : tileEntities)
        {
            try
            {
                final BlockEntityRenderState state = mc.getBlockEntityRenderDispatcher().tryExtractRenderState(tileEntity, partialTicks, null);
                if (state != null)
                {
                    state.blockPos = anchorPos.offset(tileEntity.getBlockPos());
                    renderState.blockEntityRenderStates.add(state);
                }
            }
            catch (final Exception e)
            {
                if (crashingObjects.add(tileEntity))
                {
                    LOGGER.error("Failed to extract blueprint block entity " + tileEntity, e);
                }
            }
        }

        lastGameTime = renderState.gameTime;
    }

    /**
     * @return list of entities for the instantiated renderer (potentially immediately invalid), else empty list
     */
    List<Entity> getEntities()
    {
        return entities;
    }

    /**
     * Clears cached geometry. Pure heap data in this port (no GL objects), so closing is a
     * simple drop.
     */
    @Override
    public void close()
    {
        clearVertexBuffers();
    }

    private void clearVertexBuffers()
    {
        if (cachedQuads != null)
        {
            cachedQuads.clear();
            cachedQuads = null;
        }
        if (cachedFluidVertices != null)
        {
            cachedFluidVertices.clear();
            cachedFluidVertices = null;
        }
        // PORT26 FIX (preview perf): release the GPU-resident baked layer buffers together
        // with the CPU-side cached geometry — they are rebuilt by the next init.
        closeGpuOpaqueMeshes();
        tileEntityModelData = Map.of();
    }

    private static float effectiveAlpha(final BlueprintPreviewData previewData)
    {
        final float override = previewData.getOverridePreviewTransparency();
        if (override != -1)
        {
            return Mth.clamp(override, 0, 1);
        }
        return Structurize.getConfig().getClient().rendererTransparency.get().floatValue();
    }

    /**
     * The depth-biased moving-block render types for the blueprint preview — copies of the
     * vanilla moving-block types (used by pistons), see {@link BlueprintRenderTypes}.
     *
     * <p>PORT26 FIX (preview z-fighting): the preview is drawn after the world geometry with
     * LEQUAL depth, and its vertex positions are baked through a Java-side pose while terrain
     * positions come through the section-offset shader path — the resulting depth values of a
     * preview block placed inside existing world blocks differ from the world block's by
     * float ULPs that change every frame, so coplanar faces flipped the LEQUAL test
     * frame-to-frame ("preview blocks in the ground flicker"). The vanilla fix for coplanar
     * overlay geometry is a polygon-offset depth bias toward the camera — cf.
     * {@code RenderPipelines.CRUMBLING} (break-progress decals drawn on block faces) which
     * uses {@code DepthStencilState(LEQUAL, write=false, -1.0F, -10.0F)}. The preview uses
     * the same bias (with depth writes kept on for the opaque layers, like vanilla
     * {@code TEXT_POLYGON_OFFSET}, so the preview still self-occludes) — preview faces now
     * consistently win against coplanar world faces, exactly like the 1.21.1 preview which
     * overdraw the terrain it was placed in.</p>
     *
     * <p>PORT26-compat (Iris &amp; shader packs): with an active pack the custom preview
     * pipelines are not in the pack's program override list ("Missing program …" — draws
     * invisible). Degrade to the ShaderFallbackRenderer types — vanilla pipelines (pack-
     * supported: Iris redirects them, e.g. piston moving blocks) with the view-offset
     * layering depth bias, same block vertex format, so the emission code is unchanged.
     * The CPU emission goes to the fallback buffers; the GPU-mesh pass (FIX v4) uses the
     * fallback setups directly — see drawOpaqueMeshes.</p>
     */
    private static RenderType previewMovingBlockType(final ChunkSectionLayer layer)
    {
        // PORT26-compat (Iris & shader packs): with an active pack the custom preview
        // pipelines are not in the pack's program override list (they render invisible,
        // and the manual mesh pass crashes mid-frame). Degrade to the vanilla
        // moving-block types (pack-supported — pistons render correctly under packs) —
        // same block vertex format, so the emission code below is unchanged. The
        // buffers come from ShaderFallbackRenderer (deferred flush at AfterLevel).
        if (ShaderPackCompat.isShaderPackActive())
        {
            return switch (layer)
            {
                case SOLID -> ShaderFallbackRenderer.solidType();
                case CUTOUT -> ShaderFallbackRenderer.cutoutType();
                case TRANSLUCENT -> ShaderFallbackRenderer.translucentType();
            };
        }
        return switch (layer)
        {
            case SOLID -> BlueprintRenderTypes.PREVIEW_SOLID;
            case CUTOUT -> BlueprintRenderTypes.PREVIEW_CUTOUT;
            case TRANSLUCENT -> BlueprintRenderTypes.PREVIEW_TRANSLUCENT;
        };
    }

    /**
     * PORT26-compat (Iris &amp; shader packs): the preview emission buffer. Without
     * shaders the shared game buffer source (flushed right here, per-layer); with
     * an active pack the deferred fallback source (flushed mid-frame by
     * {@code ShaderFallbackRenderer#flush} at the regular render-stage points —
     * see its class docs).
     */
    private static VertexConsumer previewBuffer(final RenderType type, final MultiBufferSource.BufferSource bufferSource)
    {
        if (ShaderPackCompat.isShaderPackActive())
        {
            return ShaderFallbackRenderer.buffers().getBuffer(type);
        }
        return bufferSource.getBuffer(type);
    }

    /**
     * PORT26-compat (Iris &amp; shader packs): ghost shell type — the custom ghost
     * pipeline degrades to the vanilla translucent moving-block type under packs
     * (same format; the per-vertex alpha still blends).
     */
    private static RenderType ghostType()
    {
        return ShaderPackCompat.isShaderPackActive() ? ShaderFallbackRenderer.translucentType() : BlueprintRenderTypes.BLUEPRINT_GHOST;
    }

    /**
     * PORT26 replacement of {@code VertexConsumer#putBlockBakedQuad} with an optional alpha
     * override for the ghost/transparency mode (the old OpenGL blend-color hack is not
     * possible with render pipelines — alpha is baked into the vertex color instead).
     *
     * <p>PORT26 FIX (batch 24): mirrors {@link VertexConsumer#putBakedQuad(PoseStack.Pose,
     * BakedQuad, QuadInstance)} — positions AND the normal are transformed by the pose before
     * being written, so the camera-relative translation above actually reaches the vertices
     * (see the note in {@link #drawUnsafe}).</p>
     *
     * @param minLight light floor under shader packs (0 = no clamping), see
     *                 {@link #PACK_PREVIEW_LIGHT_FLOOR}.
     */
    private static void putQuad(final VertexConsumer consumer, final PoseStack.Pose pose, final CachedBlockQuad cachedQuad, final int alphaByte, final int minLight)
    {
        final BakedQuad quad = cachedQuad.quad();
        final QuadInstance instance = cachedQuad.instance();
        final Vector3fc normal = quad.direction().getUnitVec3f();
        final int lightEmission = quad.materialInfo().lightEmission();
        final Matrix4f matrix = pose.pose();
        final Vector3f transformedNormal = pose.transformNormal(normal, new Vector3f());
        final Vector3f transformedPos = new Vector3f();

        for (int vertex = 0; vertex < 4; vertex++)
        {
            final Vector3fc position = quad.position(vertex);
            final long packedUv = quad.packedUV(vertex);
            int color = instance.getColor(vertex);
            if (alphaByte >= 0)
            {
                color = (color & 0x00FFFFFF) | (alphaByte << 24);
            }
            final int light = floorLight(instance.getLightCoordsWithEmission(vertex, lightEmission), minLight);
            matrix.transformPosition(position.x() + cachedQuad.x(), position.y() + cachedQuad.y(), position.z() + cachedQuad.z(), transformedPos);
            consumer.addVertex(transformedPos.x(), transformedPos.y(), transformedPos.z(),
              color,
              UVPair.unpackU(packedUv), UVPair.unpackV(packedUv),
              instance.overlayCoords(), light,
              transformedNormal.x(), transformedNormal.y(), transformedNormal.z());
        }
    }

    /**
     * Replays a cached fluid vertex through the pose — the fluid counterpart of
     * {@link #putQuad}, with the same optional alpha override for ghost mode and the
     * shader-pack light floor.
     */
    private static void putFluidVertex(final VertexConsumer consumer, final PoseStack.Pose pose, final CachedFluidVertex vertex, final int alphaByte, final int minLight)
    {
        int color = vertex.color();
        if (alphaByte >= 0)
        {
            color = (color & 0x00FFFFFF) | (alphaByte << 24);
        }
        final int light = floorLight(vertex.light(), minLight);
        final Vector3f transformedPos = pose.pose().transformPosition(vertex.x(), vertex.y(), vertex.z(), new Vector3f());
        final Vector3f transformedNormal = pose.transformNormal(vertex.nx(), vertex.ny(), vertex.nz(), new Vector3f());
        consumer.addVertex(transformedPos.x(), transformedPos.y(), transformedPos.z(),
          color,
          vertex.u(), vertex.v(),
          vertex.overlay(), light,
          transformedNormal.x(), transformedNormal.y(), transformedNormal.z());
    }

    /**
     * PORT26-compat (Iris & shader packs): clamps the packed light coords up to the given
     * per-channel floor — no-op with {@code minLight == 0}. See
     * {@link #PACK_PREVIEW_LIGHT_FLOOR} for the problem this solves (pitch-black preview
     * areas at night under realistic-darkness packs).
     *
     * @param light    the packed light coords (block &lt;&lt; 4 | sky &lt;&lt; 20).
     * @param minLight the per-channel floor, 0 to disable.
     * @return the clamped packed light coords.
     */
    private static int floorLight(final int light, final int minLight)
    {
        if (minLight <= 0)
        {
            return light;
        }
        return LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(light), minLight), Math.max(LightCoordsUtil.sky(light), minLight));
    }

    /**
     * Records the vertices {@link FluidRenderer} emits. The fluid renderer always uses the
     * 11-argument {@code addVertex} form (see {@code FluidRenderer#vertex}), so the individual
     * setters are no-ops; section-relative coordinates are rebased to blueprint-local space by
     * adding the current section origin from the shared {@code int[3]}.
     */
    private static final class FluidVertexRecorder implements VertexConsumer
    {
        private final int[] sectionOrigin;
        private final List<CachedFluidVertex> vertices = new ArrayList<>();

        private FluidVertexRecorder(final int[] sectionOrigin)
        {
            this.sectionOrigin = sectionOrigin;
        }

        List<CachedFluidVertex> vertices()
        {
            return vertices;
        }

        @Override
        public void addVertex(final float x, final float y, final float z, final int color, final float u, final float v,
          final int overlay, final int light, final float nx, final float ny, final float nz)
        {
            vertices.add(new CachedFluidVertex(sectionOrigin[0] + x, sectionOrigin[1] + y, sectionOrigin[2] + z,
              color, u, v, overlay, light, nx, ny, nz));
        }

        @Override
        public VertexConsumer addVertex(final float x, final float y, final float z)
        {
            return this;
        }

        @Override
        public VertexConsumer setColor(final int r, final int g, final int b, final int a)
        {
            return this;
        }

        @Override
        public VertexConsumer setColor(final int color)
        {
            return this;
        }

        @Override
        public VertexConsumer setUv(final float u, final float v)
        {
            return this;
        }

        @Override
        public VertexConsumer setUv1(final int u, final int v)
        {
            return this;
        }

        @Override
        public VertexConsumer setUv2(final int u, final int v)
        {
            return this;
        }

        @Override
        public VertexConsumer setNormal(final float x, final float y, final float z)
        {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(final float width)
        {
            return this;
        }
    }

    /**
     * Assuming there's no blend function active let's take advantage of OpenGL blend color constant
     * which doesnt require any shader changes at all.
     * More info at: https://registry.khronos.org/OpenGL-Refpages/gl4/html/glBlendColor.xhtml
     *
     * <p>PORT26: the GL blend-color trick is gone (render pipelines own the blend state now).
     * Transparency is achieved by baking alpha into the vertex colors during the quad replay
     * and drawing through {@link BlueprintRenderTypes#BLUEPRINT_GHOST}. This nested class is
     * kept as the threshold/flag holder that existing ported code (WorldRenderContext,
     * ClientConfiguration) references.</p>
     */
    public static class TransparencyHack
    {
        public static final float THRESHOLD = 0.99f;
        protected static boolean applied = false;

        private TransparencyHack()
        {
            throw new IllegalStateException("Utility class");
        }
    }

    /**
     * Custom render types for the blueprint preview.
     *
     * <p>PORT26 FIX (preview z-fighting, "building tool preview blocks in the ground
     * flicker"): the preview replays its cached block quads after the world geometry with a
     * LEQUAL depth test, but the preview vertex positions are transformed through a Java-side
     * pose while world terrain goes through the section-offset shader path — for a preview
     * block placed inside existing world blocks the two depth values differ by float ULPs
     * that change with the camera every frame, so coplanar faces flipped the LEQUAL test
     * frame-to-frame (flicker). Fix: polygon-offset depth bias toward the camera — the same
     * technique vanilla 26.1 uses for coplanar overlay geometry ({@code RenderPipelines.CRUMBLING},
     * the break-progress decals drawn directly on block faces, uses
     * {@code DepthStencilState(LEQUAL, write=false, -1.0F, -10.0F)}). The preview pipelines
     * below are byte-for-byte copies of the vanilla moving-block pipelines
     * (SOLID_BLOCK/CUTOUT_BLOCK/TRANSLUCENT_BLOCK, cf. {@code RenderTypes#createMovingBlockSetup})
     * with that depth bias added (depth writes stay enabled for the layers, like vanilla
     * {@code TEXT_POLYGON_OFFSET}, so the preview still self-occludes correctly).</p>
     */
    public static final class BlueprintRenderTypes
    {
        private BlueprintRenderTypes()
        {
            throw new IllegalStateException("Utility class");
        }

        /**
         * Depth bias (polygon offset) applied to all preview geometry: scale -1.0 / constant
         * -10.0 — the exact values vanilla uses for coplanar overlay decals (CRUMBLING).
         * Negative bias moves the fragments toward the camera, so preview faces consistently
         * win the LEQUAL depth test against coplanar world faces.
         */
        private static final DepthStencilState PREVIEW_DEPTH_BIAS = new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true, -1.0F, -10.0F);

        /**
         * Same bias, but without depth writes — used for the blended ghost pass (crumbling-style:
         * a translucent overlay must not stamp its (biased) depth into the buffer it shares with
         * later world passes).
         */
        private static final DepthStencilState PREVIEW_DEPTH_BIAS_NO_WRITE = new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -10.0F);

        /**
         * Copy of vanilla {@code RenderPipelines.SOLID_BLOCK} (moving block) + preview depth bias.
         */
        private static final RenderPipeline PREVIEW_SOLID_PIPELINE = RenderPipeline.builder(RenderPipelines.BLOCK_SNIPPET)
          .withLocation(Identifier.fromNamespaceAndPath("structurize", "pipeline/preview_solid_block"))
          .withDepthStencilState(PREVIEW_DEPTH_BIAS)
          .build();

        /**
         * Copy of vanilla {@code RenderPipelines.CUTOUT_BLOCK} (moving block) + preview depth bias.
         */
        private static final RenderPipeline PREVIEW_CUTOUT_PIPELINE = RenderPipeline.builder(RenderPipelines.BLOCK_SNIPPET)
          .withLocation(Identifier.fromNamespaceAndPath("structurize", "pipeline/preview_cutout_block"))
          .withShaderDefine("ALPHA_CUTOUT", 0.5F)
          .withDepthStencilState(PREVIEW_DEPTH_BIAS)
          .build();

        /**
         * Copy of vanilla {@code RenderPipelines.TRANSLUCENT_BLOCK} (moving block) + preview
         * depth bias (writes kept on, mirroring the vanilla translucent pipeline).
         */
        private static final RenderPipeline PREVIEW_TRANSLUCENT_PIPELINE = RenderPipeline.builder(RenderPipelines.BLOCK_SNIPPET)
          .withLocation(Identifier.fromNamespaceAndPath("structurize", "pipeline/preview_translucent_block"))
          .withShaderDefine("ALPHA_CUTOUT", 0.01F)
          .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
          .withDepthStencilState(PREVIEW_DEPTH_BIAS)
          .build();

        public static final RenderSetup PREVIEW_SOLID_SETUP = RenderSetup.builder(PREVIEW_SOLID_PIPELINE)
            .useLightmap()
            .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS,
              () -> RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, true))
            .createRenderSetup();

        public static final RenderType PREVIEW_SOLID = RenderType.create("structurize_preview_solid_block", PREVIEW_SOLID_SETUP);

        public static final RenderSetup PREVIEW_CUTOUT_SETUP = RenderSetup.builder(PREVIEW_CUTOUT_PIPELINE)
            .useLightmap()
            .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS,
              () -> RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, true))
            .createRenderSetup();

        public static final RenderType PREVIEW_CUTOUT = RenderType.create("structurize_preview_cutout_block", PREVIEW_CUTOUT_SETUP);

        public static final RenderType PREVIEW_TRANSLUCENT = RenderType.create("structurize_preview_translucent_block",
          RenderSetup.builder(PREVIEW_TRANSLUCENT_PIPELINE)
            .useLightmap()
            .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS,
              () -> RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, true))
            .sortOnUpload()
            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
            .createRenderSetup());

        /**
         * Mirrors the vanilla translucent moving-block pipeline, but without the alpha-cutout
         * discard so low-alpha ghost previews stay visible.
         *
         * <p>PORT26 FIX (preview z-fighting): depth state changed from
         * {@code DepthStencilState.DEFAULT} (LEQUAL + depth writes, no bias) to the crumbling-style
         * biased, write-less state — the ghost shell stopped z-fighting against coplanar world
         * faces and no longer pollutes the depth buffer for the passes drawn after it
         * (particles/weather/clouds).</p>
         */
        private static final RenderPipeline BLUEPRINT_GHOST_PIPELINE = RenderPipeline.builder(RenderPipelines.BLOCK_SNIPPET)
          .withLocation(Identifier.fromNamespaceAndPath("structurize", "pipeline/blueprint_ghost"))
          .withShaderDefine("ALPHA_CUTOUT", 0.01F)
          .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
          .withDepthStencilState(PREVIEW_DEPTH_BIAS_NO_WRITE)
          .build();

        public static final RenderType BLUEPRINT_GHOST = RenderType.create("structurize_blueprint_ghost",
          RenderSetup.builder(BLUEPRINT_GHOST_PIPELINE)
            .useLightmap()
            .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS,
              () -> RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, true))
            .sortOnUpload()
            .createRenderSetup());

        /**
         * PORT26: new — pipelines must be registered (mod bus, client) before use.
         */
        public static void registerPipelines(final RegisterRenderPipelinesEvent event)
        {
            event.registerPipeline(BLUEPRINT_GHOST_PIPELINE);
            event.registerPipeline(PREVIEW_SOLID_PIPELINE);
            event.registerPipeline(PREVIEW_CUTOUT_PIPELINE);
            event.registerPipeline(PREVIEW_TRANSLUCENT_PIPELINE);
        }

        /**
         * Register our buffers.
         */
        public static void registerBuffer(final RegisterRenderBuffersEvent event)
        {
            event.registerRenderBuffer(BLUEPRINT_GHOST);
            event.registerRenderBuffer(PREVIEW_SOLID);
            event.registerRenderBuffer(PREVIEW_CUTOUT);
            event.registerRenderBuffer(PREVIEW_TRANSLUCENT);
        }
    }

    /**
     * Minimal adapter that exposes the {@link BlueprintBlockAccess} fake level to the 26.1
     * block tessellator, which takes the client-side {@link BlockAndTintGetter} interface
     * (not the world one the fake level implements). Everything is delegated 1:1.
     */
    private final class RenderingView implements BlockAndTintGetter
    {
        @Override
        public CardinalLighting cardinalLighting()
        {
            return blockAccess.cardinalLighting();
        }

        @Override
        public LevelLightEngine getLightEngine()
        {
            return blockAccess.getLightEngine();
        }

        @Override
        public int getBlockTint(final BlockPos pos, final ColorResolver color)
        {
            // PORT26: Level no longer carries getBlockTint (it moved to the client
            // BlockAndTintGetter); resolve via the fake level's biome like the 1.21.1
            // default implementation did (resolver.getColor(biome, x, z)).
            return color.getColor(blockAccess.getBiome(pos).value(), pos.getX(), pos.getZ());
        }

        @Override
        public BlockEntity getBlockEntity(final BlockPos pos)
        {
            return blockAccess.getBlockEntity(pos);
        }

        @Override
        public BlockState getBlockState(final BlockPos pos)
        {
            return blockAccess.getBlockState(pos);
        }

        /**
         * PORT26 FIX (preview placeholder blocks): serve the blueprint block entities' model
         * data to the block tessellator. NeoForge's {@code IBlockGetterExtension#getModelData}
         * defaults to {@link ModelData#EMPTY}; without this override every dynamic model —
         * Domum Ornamentum's materially-textured compat blocks (the bulk of the byzantine
         * style) — fell back to its inner placeholder model during preview baking, so the
         * whole structure rendered as grey placeholder blocks.
         */
        @Override
        public ModelData getModelData(final BlockPos pos)
        {
            return tileEntityModelData.getOrDefault(pos, ModelData.EMPTY);
        }

        @Override
        public FluidState getFluidState(final BlockPos pos)
        {
            return blockAccess.getFluidState(pos);
        }

        @Override
        public int getHeight()
        {
            return blockAccess.getHeight();
        }

        @Override
        public int getMinY()
        {
            return blockAccess.getMinY();
        }
    }
}
