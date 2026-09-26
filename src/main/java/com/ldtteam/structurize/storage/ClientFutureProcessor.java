package com.ldtteam.structurize.storage;

import com.ldtteam.structurize.api.Log;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.NotNull;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Waits for blueprint futures to finish loading and then processes them.
 */
public class ClientFutureProcessor
{
    /**
     * Queue for processing blueprint futures.
     */
    private static final Queue<BlueprintProcessingData> blueprintConsumerQueue = new LinkedList<>();

    /**
     * Queue for processing data futures.
     */
    private static final Queue<BlueprintDataProcessingData> blueprintDataConsumerQueue = new LinkedList<>();

    /**
     * Queue processing data to be handled on tick.
     * @param processingData the data to be processed.
     */
    public static void queueBlueprint(@NotNull final ClientFutureProcessor.BlueprintProcessingData processingData)
    {
        blueprintConsumerQueue.add(processingData);
    }

    /**
     * Queue processing data to be handled on tick.
     * @param processingData the data to be processed.
     */
    public static void queueBlueprintData(@NotNull final ClientFutureProcessor.BlueprintDataProcessingData processingData)
    {
        blueprintDataConsumerQueue.add(processingData);
    }

    @SubscribeEvent
    public static void onWorldTick(final ClientTickEvent.Post event)
    {
        if (!blueprintConsumerQueue.isEmpty() && blueprintConsumerQueue.peek().blueprintFuture.isDone())
        {
            final BlueprintProcessingData data = blueprintConsumerQueue.poll();
            try
            {
                data.consumer.accept(data.blueprintFuture.get());
            }
            catch (InterruptedException | ExecutionException e)
            {
                // PORT26 FIX (batch 24): stack-trace to the mod logger — this is the exact
                // place a failing blueprint store/load silently swallowed the follow-up
                // action (e.g. the shape tool never sending its placement message).
                Log.getLogger().error("Failed to process client blueprint future (queued action was NOT executed)", e);
            }
        }

        if (!blueprintDataConsumerQueue.isEmpty() && blueprintDataConsumerQueue.peek().blueprintDataFuture.isDone())
        {
            final BlueprintDataProcessingData data = blueprintDataConsumerQueue.poll();
            try
            {
                data.consumer.accept(data.blueprintDataFuture.get());
            }
            catch (InterruptedException | ExecutionException e)
            {
                // PORT26 FIX (batch 24): see above
                Log.getLogger().error("Failed to process client blueprint data future (queued action was NOT executed)", e);
            }
        }
    }

    /**
     * Data to be processed.
     */
    public record BlueprintProcessingData(Future<Blueprint> blueprintFuture, Consumer<Blueprint> consumer) { }

    /**
     * Data to be processed.
     */
    public record BlueprintDataProcessingData(Future<byte[]> blueprintDataFuture, Consumer<byte[]> consumer) { }
}
