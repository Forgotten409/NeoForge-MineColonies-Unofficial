package com.ldtteam.structurize.client;

import com.ldtteam.structurize.api.Log;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * PORT26 FIX (0.7.7, preview generation lag): dedicated background pool for baking blueprint
 * preview geometry.
 *
 * <p>Problem being solved: {@link BlueprintRenderer} tessellated the whole blueprint (every
 * block through {@code ModelBlockRenderer#tesselateBlock}, plus block-entity/entity
 * instantiation) synchronously on the RENDER THREAD the first frame a preview appeared —
 * and again after every rotation/mirror click (each rotation is a new
 * {@code RenderingCacheKey} → a new renderer → a fresh bake). Players reported exactly that:
 * "still quite laggy when generating the build previews [...] only a bother with the larger
 * previews, so whatever is causing it is probably tied to block count". The bake is now
 * submitted to this pool; the render thread only installs the finished data (field writes +
 * one GPU buffer upload), so a large build costs it a millisecond instead of a multi-hundred-ms
 * freeze.</p>
 *
 * <p>Deliberately separate from {@link com.ldtteam.structurize.util.IOPool}: blueprint
 * (file IO) decoding and CPU tessellation have very different latencies, and sharing the
 * 2-thread IO pool would re-introduce the old "build tool feels laggy" starvation when a
 * big bake queued in front of blueprint loads.</p>
 */
public final class PreviewBakePool
{
    /**
     * Holds the bake queue.
     */
    private static final BlockingQueue<Runnable> bakeQueue = new LinkedBlockingDeque<>();

    /**
     * Holds the specific executor for the queue.
     */
    private static ThreadPoolExecutor executor;

    /**
     * Preview bake thread factory.
     */
    private static class PreviewBakeThreadFactory implements ThreadFactory
    {
        private int id = 0;

        @Override
        public Thread newThread(@NotNull final Runnable runnable)
        {
            final Thread thread = new Thread(runnable, "Structurize Preview Baker #" + (id++));
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((thread1, throwable) ->
              Log.getLogger().error("Structurize Preview Bake Thread errored! ", throwable));
            return thread;
        }
    }

    /**
     * Gets (lazily creating) the bake executor. Two workers: neighbour-building rendering
     * (several previews at once) bakes in parallel without serializing behind each other,
     * while still leaving enough cores for the render thread and the IO pool.
     *
     * @return the threadpool executor.
     */
    public static ThreadPoolExecutor getExecutor()
    {
        if (executor == null)
        {
            executor = new ThreadPoolExecutor(2, 2, 10, TimeUnit.SECONDS, bakeQueue, new PreviewBakeThreadFactory());
            executor.allowCoreThreadTimeOut(true);
        }
        return executor;
    }

    /**
     * Stops all running threads in this pool.
     */
    public static void shutdown()
    {
        getExecutor().shutdownNow();
        bakeQueue.clear();
        executor = null;
    }

    private PreviewBakePool()
    {
        // Hides default constructor.
    }
}
