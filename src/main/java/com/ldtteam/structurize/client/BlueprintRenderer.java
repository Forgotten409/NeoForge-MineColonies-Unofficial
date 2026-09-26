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
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
     * A baked preview layer: the GPU-resident vertex buffer (blueprint-local coordinates —
     * the camera-relative translation is supplied per frame through the global model-view
     * stack) and the index count for the shared sequential QUADS index buffer.
     */
    private record BakedLayerMesh(GpuBuffer vertexBuffer, int indexCount)
    {
    }

    private long lastGameTime;
    private Set<Object> crashingObjects = Collections.newSetFromMap(new IdentityHashMap<>());

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
        uploadOpaqueLayerMeshes();
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
                    putQuad(builder, identityPose, cachedQuad, -1);
                }
            }
            if (hasFluids)
            {
                for (final CachedFluidVertex fluidVertex : fluidVertices)
                {
                    putFluidVertex(builder, identityPose, fluidVertex, -1);
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
                gpuOpaqueMeshes.put(layer, new BakedLayerMesh(vertexBuffer, mesh.drawState().indexCount()));
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
        LOGGER.error("Problem during blueprint rendering:\n{}", report);
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

        if (ghost)
        {
            final VertexConsumer consumer = bufferSource.getBuffer(BlueprintRenderTypes.BLUEPRINT_GHOST);
            for (final List<CachedBlockQuad> quads : cachedQuads.values())
            {
                for (final CachedBlockQuad cachedQuad : quads)
                {
                    putQuad(consumer, pose, cachedQuad, alphaByte);
                }
            }
            // fluids share the ghost buffer (alpha applied per-vertex like the block quads)
            if (cachedFluidVertices != null)
            {
                for (final List<CachedFluidVertex> fluidVertices : cachedFluidVertices.values())
                {
                    for (final CachedFluidVertex fluidVertex : fluidVertices)
                    {
                        putFluidVertex(consumer, pose, fluidVertex, alphaByte);
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
            drawOpaqueMeshes(pose, mc);

            final List<CachedBlockQuad> translucentQuads = cachedQuads.get(ChunkSectionLayer.TRANSLUCENT);
            final List<CachedFluidVertex> translucentFluids = cachedFluidVertices == null ? null : cachedFluidVertices.get(ChunkSectionLayer.TRANSLUCENT);
            final boolean hasTranslucent = (translucentQuads != null && !translucentQuads.isEmpty())
                                             || (translucentFluids != null && !translucentFluids.isEmpty());
            final boolean solidDrawnViaMesh = gpuOpaqueMeshes != null && gpuOpaqueMeshes.containsKey(ChunkSectionLayer.SOLID);
            final boolean cutoutDrawnViaMesh = gpuOpaqueMeshes != null && gpuOpaqueMeshes.containsKey(ChunkSectionLayer.CUTOUT);
            if (hasTranslucent || !solidDrawnViaMesh || !cutoutDrawnViaMesh)
            {
                final VertexConsumer consumer = bufferSource.getBuffer(previewMovingBlockType(ChunkSectionLayer.TRANSLUCENT));
                if (translucentQuads != null && !translucentQuads.isEmpty())
                {
                    for (final CachedBlockQuad cachedQuad : translucentQuads)
                    {
                        putQuad(consumer, pose, cachedQuad, -1);
                    }
                }
                if (translucentFluids != null && !translucentFluids.isEmpty())
                {
                    for (final CachedFluidVertex fluidVertex : translucentFluids)
                    {
                        putFluidVertex(consumer, pose, fluidVertex, -1);
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
                    final VertexConsumer layerConsumer = bufferSource.getBuffer(previewMovingBlockType(layer));
                    if (quads != null)
                    {
                        for (final CachedBlockQuad cachedQuad : quads)
                        {
                            putQuad(layerConsumer, pose, cachedQuad, -1);
                        }
                    }
                    if (fluidVertices != null)
                    {
                        for (final CachedFluidVertex fluidVertex : fluidVertices)
                        {
                            putFluidVertex(layerConsumer, pose, fluidVertex, -1);
                        }
                    }
                }
            }
        }
    }

    /**
     * Draws the baked SOLID/CUTOUT layers through a dedicated render pass — the pattern of
     * vanilla's {@code ChunkSectionsToRender#renderGroup}: one pass, the render setup's own
     * texture bindings (block atlas + lightmap), the shared sequential QUADS index buffer,
     * and one dynamic-uniform write carrying the model-view matrix.
     *
     * <p>PORT26 FIX (preview perf): the vertex buffers were uploaded once at init in
     * blueprint-local coordinates; the camera-relative translation is supplied per frame by
     * pushing the pose onto the global model-view stack ({@code RenderSystem.getModelViewStack()})
     * — mathematically identical to the previous per-vertex pose baking
     * ({@code M * pose * v == (M * pose) * v}), but with zero per-vertex CPU work.</p>
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

        final Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        try
        {
            modelViewStack.mul(pose.pose());
            final RenderTarget renderTarget = mc.getMainRenderTarget();
            final GpuTextureView colorTexture = RenderSystem.outputColorTextureOverride != null
                                                   ? RenderSystem.outputColorTextureOverride
                                                   : renderTarget.getColorTextureView();
            final GpuTextureView depthTexture = renderTarget.useDepth
                                                   ? (RenderSystem.outputDepthTextureOverride != null
                                                        ? RenderSystem.outputDepthTextureOverride
                                                        : renderTarget.getDepthTextureView())
                                                   : null;

            final RenderPass renderPass = RenderSystem.getDevice()
              .createCommandEncoder()
              .createRenderPass(() -> "Structurize blueprint preview (opaque layers)",
                colorTexture, OptionalInt.empty(), depthTexture, OptionalDouble.empty());
            try
            {
                RenderSystem.bindDefaultUniforms(renderPass);

                // texture bindings exactly as the preview render setups declare them
                // (Sampler0 = block atlas, Sampler2 = lightmap from useLightmap())
                for (final Map.Entry<String, RenderSetup.TextureAndSampler> entry : BlueprintRenderTypes.PREVIEW_SOLID_SETUP.getTextures().entrySet())
                {
                    renderPass.bindTexture(entry.getKey(), entry.getValue().textureView(), entry.getValue().sampler());
                }

                final GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                  RenderSystem.getModelViewMatrix(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());

                final RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                for (final ChunkSectionLayer layer : new ChunkSectionLayer[] {ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT})
                {
                    final BakedLayerMesh baked = gpuOpaqueMeshes.get(layer);
                    if (baked == null)
                    {
                        continue;
                    }

                    renderPass.setPipeline(previewMovingBlockType(layer).pipeline());
                    renderPass.setUniform("DynamicTransforms", dynamicTransforms);
                    renderPass.setVertexBuffer(0, baked.vertexBuffer());
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
     */
    private static RenderType previewMovingBlockType(final ChunkSectionLayer layer)
    {
        return switch (layer)
        {
            case SOLID -> BlueprintRenderTypes.PREVIEW_SOLID;
            case CUTOUT -> BlueprintRenderTypes.PREVIEW_CUTOUT;
            case TRANSLUCENT -> BlueprintRenderTypes.PREVIEW_TRANSLUCENT;
        };
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
     */
    private static void putQuad(final VertexConsumer consumer, final PoseStack.Pose pose, final CachedBlockQuad cachedQuad, final int alphaByte)
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
            final int light = instance.getLightCoordsWithEmission(vertex, lightEmission);
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
     * {@link #putQuad}, with the same optional alpha override for ghost mode.
     */
    private static void putFluidVertex(final VertexConsumer consumer, final PoseStack.Pose pose, final CachedFluidVertex vertex, final int alphaByte)
    {
        int color = vertex.color();
        if (alphaByte >= 0)
        {
            color = (color & 0x00FFFFFF) | (alphaByte << 24);
        }
        final Vector3f transformedPos = pose.pose().transformPosition(vertex.x(), vertex.y(), vertex.z(), new Vector3f());
        final Vector3f transformedNormal = pose.transformNormal(vertex.nx(), vertex.ny(), vertex.nz(), new Vector3f());
        consumer.addVertex(transformedPos.x(), transformedPos.y(), transformedPos.z(),
          color,
          vertex.u(), vertex.v(),
          vertex.overlay(), vertex.light(),
          transformedNormal.x(), transformedNormal.y(), transformedNormal.z());
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
