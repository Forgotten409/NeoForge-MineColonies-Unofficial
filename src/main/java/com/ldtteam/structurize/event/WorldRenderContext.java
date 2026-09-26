package com.ldtteam.structurize.event;

import com.ldtteam.structurize.Structurize;
import com.ldtteam.structurize.blockentities.interfaces.IBlueprintDataProviderBE;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.client.BlueprintRenderer.TransparencyHack;
import com.ldtteam.structurize.items.ItemTagTool.TagData;
import com.ldtteam.structurize.storage.rendering.RenderingCache;
import com.ldtteam.structurize.storage.rendering.types.BlueprintPreviewData;
import com.ldtteam.structurize.storage.rendering.types.BoxPreviewData;
import com.ldtteam.structurize.util.WorldRenderMacros;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;
import java.util.Map;

/**
 * For rendering into world.
 */
public class WorldRenderContext extends WorldRenderMacros
{
    static final WorldRenderContext INSTANCE = new WorldRenderContext();

    /**
     * PORT26: the Stage enum was removed — renderWithinContext now receives the concrete
     * RenderLevelStageEvent sub-event (AfterOpaqueFeatures, AfterTranslucentBlocks, ...) and
     * stage checks became class checks (see WorldRenderMacros.STAGE_FOR_LINES). The old
     * Stage.AFTER_TRANSLUCENT_BLOCKS maps to RenderLevelStageEvent.AfterTranslucentBlocks;
     * the old Stage.AFTER_BLOCK_ENTITIES maps to AfterOpaqueFeatures (entities + block
     * entities are submitted as opaque features there, per WorldRenderMacros).
     */
    @Override
    protected void renderWithinContext(final RenderLevelStageEvent event)
    {
        final double alpha = Structurize.getConfig().getClient().rendererTransparency.get();
        final boolean isAlphaApplied = alpha > 0 && alpha < TransparencyHack.THRESHOLD;

        final Class<? extends RenderLevelStageEvent> when = isAlphaApplied
            ? RenderLevelStageEvent.AfterTranslucentBlocks.class
            : RenderLevelStageEvent.AfterOpaqueFeatures.class;
        // otherwise even worse sorting issues arise
        if (when.isInstance(event))
        {
            renderBlueprints();
        }

        if (WorldRenderMacros.STAGE_FOR_LINES.isInstance(event))
        {
            renderBoxes();
            renderTagTool();
        }
    }

    private void renderBlueprints()
    {
        // PORT26 (batch 41): the batch-39 [preview-diag] throttled state log confirmed the
        // preview pipeline works end-to-end (queued → future resolves → rendered, and the
        // user verified previews in-game); removed to stop the periodic log spam.
        for (final BlueprintPreviewData previewData : RenderingCache.getBlueprintsToRender())
        {
            final Blueprint blueprint = previewData.getBlueprint();

            if (blueprint != null)
            {
                // PORT26: Minecraft#getProfiler() removed -> thread-local Profiler#get()
                Profiler.get().push("struct_render");

                renderBlueprint(previewData, previewData.getPos());

                Profiler.get().pop();
            }
        }
    }

    private void renderBoxes()
    {
        for (final BlueprintPreviewData previewData : RenderingCache.getBlueprintsToRender())
        {
            final Blueprint blueprint = previewData.getBlueprint();

            if (blueprint != null)
            {
                final BlockPos anchor = blueprint.getPrimaryBlockOffset();

                // PORT26: Minecraft#getProfiler() removed -> thread-local Profiler#get()
                Profiler.get().push("struct_render");
                pushPoseCameraToPos(previewData.getPos().subtract(anchor));
                try
                {
                    renderWhiteLineBox(BlockPos.ZERO,
                        new BlockPos(blueprint.getSizeX() - 1, blueprint.getSizeY() - 1, blueprint.getSizeZ() - 1),
                        DEFAULT_LINE_WIDTH);
                    renderRedGlintLineBox(anchor, anchor, DEFAULT_LINE_WIDTH);
                }
                // PORT26 FIX (play-test #7): the level renderer's pose stack must never leak a
                // push — an exception between push and pop makes LevelRenderer#checkPoseStack
                // throw "Pose stack not empty" at the end of the frame and crashes the game
                finally
                {
                    popPose();
                    Profiler.get().pop();
                }
            }
        }

        for (final BoxPreviewData previewData : RenderingCache.getBoxesToRender())
        {
            final BlockPos root = previewData.pos1();

            // PORT26: Minecraft#getProfiler() removed -> thread-local Profiler#get()
            Profiler.get().push("struct_box");
            pushPoseCameraToPos(root);
            try
            {
                // Used to render a red box around a scan's Primary offset (primary block)
                renderWhiteLineBox(BlockPos.ZERO, previewData.pos2().subtract(root), DEFAULT_LINE_WIDTH);
                previewData.anchor().map(pos -> pos.subtract(root)).ifPresent(pos -> renderRedGlintLineBox(pos, pos, DEFAULT_LINE_WIDTH));
            }
            finally
            {
                popPose();
                Profiler.get().pop();
            }
        }
    }

    private void renderTagTool()
    {
        final Player player = mc.player;
        final ItemStack itemStack = player.getItemInHand(InteractionHand.MAIN_HAND);
        final TagData tags = TagData.readFromItemStack(itemStack);
        if (tags.anchorPos().isPresent())
        {
            final BlockPos tagAnchor = tags.anchorPos().get();
            final BlockEntity te = player.level().getBlockEntity(tagAnchor);

            // PORT26: Minecraft#getProfiler() removed -> thread-local Profiler#get()
            Profiler.get().push("struct_tags");
            pushPoseCameraToPos(tagAnchor);
            try
            {
                if (te instanceof final IBlueprintDataProviderBE blueprintProvider)
                {
                    final Map<BlockPos, List<String>> tagPosList = blueprintProvider.getWorldTagPosMap();

                    for (final Map.Entry<BlockPos, List<String>> entry : tagPosList.entrySet())
                    {
                        final BlockPos pos = entry.getKey().subtract(tagAnchor);
                        renderWhiteLineBox(pos, pos, DEFAULT_LINE_WIDTH);
                        renderDebugText(pos, entry.getKey(), entry.getValue(), true, 3);
                    }
                }
                renderRedGlintLineBox(BlockPos.ZERO, BlockPos.ZERO, DEFAULT_LINE_WIDTH);
            }
            // PORT26 FIX (play-test #7): see renderBoxes — never leak the pose push
            finally
            {
                popPose();
                Profiler.get().pop();
            }
        }
    }
}
