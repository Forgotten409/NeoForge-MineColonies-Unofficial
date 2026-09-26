package com.ldtteam.structurize.client;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.ldtteam.structurize.api.Log;
import com.ldtteam.structurize.storage.rendering.RenderingCache;
import com.ldtteam.structurize.storage.rendering.types.BlueprintPreviewData;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The Blueprint render handler on the client side.
 *
 * <p>PORT26: same guava-cached-renderer architecture as 1.21.1, plus the new
 * {@link #extractRenderStates(ExtractLevelRenderStateEvent)} entry point — since 26.1 renders
 * entities/block entities from render states, the blueprint preview appends its entity and
 * block entity states to the level render state during extraction, and vanilla submits them
 * with correct lighting, fog and pipelines (the 1.21.1 renderer drew them manually through
 * the render dispatchers, which no longer exposes a render(Level, ...) path).</p>
 */
public final class BlueprintHandler
{
    /**
     * A static instance on the client.
     */
    private static final BlueprintHandler ourInstance = new BlueprintHandler();
    /**
     * How long are cache entries valid
     */
    public static final int CACHE_EXPIRE_SECONDS = 45;
    /**
     * How often should cache cleanup happen
     */
    public static final int CACHE_EXPIRE_CHECK_SECONDS = CACHE_EXPIRE_SECONDS / 3;

    private final LoadingCache<RenderingCacheKey, BlueprintRenderer> rendererCache = CacheBuilder.newBuilder()
        .expireAfterAccess(CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS)
        .<RenderingCacheKey, BlueprintRenderer>removalListener(entry -> entry.getValue().close())
        .build(new CacheLoader<>()
        {
            @Override
            public BlueprintRenderer load(final RenderingCacheKey key)
            {
                return BlueprintRenderer.buildRendererForBlueprint(key.blueprint());
            }
        });

    /**
     * PORT26 FIX (play-test #7 crash hardening): render keys whose renderer CONSTRUCTION
     * failed — guava does not cache loader failures, so without this set every frame would
     * retry the constructor and re-log the error (once per frame log spam). Cleared together
     * with the renderer cache so a fixed data source (e.g. re-placed build tool) recovers.
     */
    private final Set<RenderingCacheKey> brokenRenderKeys = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Private constructor to hide public one.
     */
    private BlueprintHandler()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Get the static instance.
     *
     * @return a static instance of this class.
     */
    public static BlueprintHandler getInstance()
    {
        return ourInstance;
    }

    /**
     * Draw a blueprint at given pos.
     *
     * @param previewData the blueprint and context to draw.
     * @param pos         position to render at
     * @param ctx         rendering event
     */
    public void draw(final BlueprintPreviewData previewData, final BlockPos pos, final RenderLevelStageEvent ctx)
    {
        if (previewData == null || previewData.getBlueprint() == null)
        {
            Log.getLogger().warn("Trying to draw null blueprint!");
            return;
        }
        if (brokenRenderKeys.contains(previewData.getRenderKey()))
        {
            return;
        }
        Profiler.get().push("struct_render_cache");
        try
        {
            rendererCache.getUnchecked(previewData.getRenderKey()).draw(previewData, pos, ctx);
        }
        catch (final Exception | LinkageError e)
        {
            // renderer construction (CacheLoader) failed — blacklist the key so the failure
            // is logged ONCE instead of once per frame; guava wraps the loader throw in
            // UncheckedExecutionException / ExecutionError depending on its type
            brokenRenderKeys.add(previewData.getRenderKey());
            Log.getLogger().error("Failed to create blueprint renderer — blacklisting this preview until the render cache clears", e);
        }
        finally
        {
            Profiler.get().pop();
        }
    }

    /**
     * Extracts entity/block-entity render states for every active blueprint preview into the
     * level render state. Fired once per frame, after vanilla extracted its own states.
     *
     * @param event the extract event
     */
    public void extractRenderStates(final ExtractLevelRenderStateEvent event)
    {
        final LevelRenderState renderState = event.getRenderState();
        final Camera camera = event.getCamera();
        final DeltaTracker deltaTracker = event.getDeltaTracker();

        for (final BlueprintPreviewData previewData : RenderingCache.getBlueprintsToRender())
        {
            if (previewData == null || previewData.getBlueprint() == null || previewData.getPos() == null)
            {
                continue;
            }

            Profiler.get().push("struct_render_extract");
            try
            {
                rendererCache.getUnchecked(previewData.getRenderKey()).extractInto(previewData, renderState, camera, deltaTracker);
            }
            catch (final Exception | LinkageError e)
            {
                // never let a broken preview kill the level render state extraction
                // (LinkageError included: guava's LoadingCache wraps renderer-constructor
                // failures in ExecutionError, which a plain "catch Exception" misses —
                // play-test #7's crash-hardening)
                Log.getLogger().error("Failed to extract blueprint render states", e);
            }
            finally
            {
                Profiler.get().pop();
            }
        }
    }

    /**
     * Cleans entries that are older than CACHE_EVICT_TIME.
     */
    public void cleanCache()
    {
        rendererCache.cleanUp();
    }

    /**
     * Clear all entries.
     */
    public void clearCache()
    {
        rendererCache.invalidateAll();
        brokenRenderKeys.clear();
    }

    /**
     * Draw a blueprint at list of given pos.
     *
     * @param previewData the blueprint and context to draw.
     * @param points      list of positions to render at
     * @param ctx         rendering event
     */
    public void drawAtListOfPositions(final BlueprintPreviewData previewData,
        final Collection<BlockPos> points,
        final RenderLevelStageEvent ctx)
    {
        if (points.isEmpty() || previewData == null || previewData.getBlueprint() == null)
        {
            return;
        }

        Profiler.get().push("struct_render_multi");
        try
        {
            final BlueprintRenderer renderer = rendererCache.getUnchecked(previewData.getRenderKey());

            for (final BlockPos coord : points)
            {
                renderer.draw(previewData, coord, ctx);
            }
        }
        finally
        {
            Profiler.get().pop();
        }
    }

    /**
     * @return list of entities for instantiated renderer (potentially immediately invalid), else empty list
     */
    public List<Entity> getOptionalEntitiesForBlueprint(final BlueprintPreviewData previewData)
    {
        final BlueprintRenderer renderer = rendererCache.getIfPresent(previewData.getRenderKey());
        return renderer == null ? List.of() : renderer.getEntities();
    }
}
