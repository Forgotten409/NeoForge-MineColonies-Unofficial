package com.ldtteam.structurize.event;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.structurize.api.IScrollableItem;
import com.ldtteam.structurize.api.ISpecialBlockPickItem;
import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.client.BlueprintHandler;
import com.ldtteam.structurize.client.ModKeyMappings;
import com.ldtteam.structurize.client.gui.WindowExtendedBuildTool;
import com.ldtteam.structurize.util.WorldRenderMacros;
import com.ldtteam.structurize.items.ItemScanTool;
import com.ldtteam.structurize.network.messages.ItemMiddleMouseMessage;
import com.ldtteam.structurize.network.messages.ScanToolTeleportMessage;
import com.ldtteam.structurize.storage.rendering.RenderingCache;
import com.ldtteam.structurize.storage.rendering.types.BoxPreviewData;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;

public class ClientEventSubscriber
{
    @SubscribeEvent
    public static void renderWorldLastEvent(final RenderGuiLayerEvent.Pre event)
    {
        if ((event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH) || event.getName().equals(VanillaGuiLayers.FOOD_LEVEL)) && Minecraft.getInstance().screen instanceof BOScreen &&
              ((BOScreen) Minecraft.getInstance().screen).getWindow() instanceof WindowExtendedBuildTool)
        {
             event.setCanceled(true);
        }
    }

    /**
     * Used to catch the renderWorldLastEvent in order to draw the debug nodes for pathfinding.
     *
     * <p>PORT26: RenderLevelStageEvent lost its Stage enum — it is now split into typed
     * sub-events (AfterSky, AfterOpaqueBlocks, AfterOpaqueFeatures, AfterTranslucentBlocks,
     * ...). The old single handler filtered by stage inside
     * {@code WorldRenderContext#renderWithinContext(Stage)}; the two stages that handler
     * could act on map to {@link RenderLevelStageEvent.AfterOpaqueFeatures} (old
     * AFTER_BLOCK_ENTITIES / STAGE_FOR_LINES, see WorldRenderMacros) and
     * {@link RenderLevelStageEvent.AfterTranslucentBlocks} (old AFTER_TRANSLUCENT_BLOCKS),
     * so exactly those two sub-events are subscribed here. The stage dispatch itself still
     * happens inside {@code WorldRenderContext#renderWithinContext(RenderLevelStageEvent)}.</p>
     *
     * @param event the catched event.
     */
    @SubscribeEvent
    public static void renderWorldLastEvent(final RenderLevelStageEvent.AfterOpaqueFeatures event)
    {
        WorldRenderContext.INSTANCE.renderWorldLastEvent(event);
    }

    @SubscribeEvent
    public static void renderWorldLastEvent(final RenderLevelStageEvent.AfterTranslucentBlocks event)
    {
        WorldRenderContext.INSTANCE.renderWorldLastEvent(event);
    }

    // PORT26-compat (Iris & shader packs): the old AfterLevel flush subscription was
    // removed — drawing after the frame graph's declared passes corrupted the main
    // target (black fragments) and the re-applied camera rotation double-rotated the
    // overlays. The fallback buffers are now flushed mid-frame at the end of
    // WorldRenderMacros#renderWorldLastEvent (plus the finishBuffer safety net), with
    // the exact model-view matrix the stage event carries. See ShaderFallbackRenderer.

    /**
     * PORT26: new entry point — 26.1 renders entities/block entities from render states.
     * Blueprint previews append their entity + block entity states here (after vanilla
     * extracted its own states, before anything is submitted), so vanilla submits them with
     * correct lighting/fog/pipelines. Block geometry itself is still drawn from the stage
     * events below.
     *
     * @param event the extract event.
     */
    @SubscribeEvent
    public static void onExtractLevelRenderState(final ExtractLevelRenderStateEvent event)
    {
        BlueprintHandler.getInstance().extractRenderStates(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void finishBuffers(final RenderLevelStageEvent.AfterOpaqueFeatures event)
    {
        WorldRenderContext.RenderTypes.finishBuffer(event);
    }

    /**
     * Used to catch the clientTickEvent.
     * Call renderer cache cleaning every 5 secs (100 ticks).
     *
     * @param event the catched event.
     */
    @SubscribeEvent
    public static void onClientTickEvent(final ClientTickEvent.Post event)
    {
        final Minecraft mc = Minecraft.getInstance();
        // PORT26: Minecraft#getProfiler() was removed with the profiler rework —
        // use the thread-local static Profiler#get() (same pattern as blockui port).
        Profiler.get().push("structurize");

        if (mc.level != null && mc.level.getGameTime() % (Constants.TICKS_SECOND * BlueprintHandler.CACHE_EXPIRE_CHECK_SECONDS) == 0)
        {
            Profiler.get().push("blueprint_manager_tick");
            BlueprintHandler.getInstance().cleanCache();
            Profiler.get().pop();
        }

        if (ModKeyMappings.TELEPORT.get().consumeClick() && mc.level != null && mc.player != null &&
            mc.player.getMainHandItem().getItem() instanceof ItemScanTool tool)
        {
            if (tool.onTeleport(mc.player, mc.player.getMainHandItem()))
            {
                new ScanToolTeleportMessage().sendToServer();
            }
        }

        Profiler.get().pop();
    }

    @SubscribeEvent
    public static void onPreClientTickEvent(@NotNull final ClientTickEvent.Pre event)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.level == null) return;

        if (mc.options.keyPickItem.consumeClick())
        {
            BlockPos pos = mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK ? ((BlockHitResult)mc.hitResult).getBlockPos() : null;
            if (pos != null && mc.level.getBlockState(pos).isAir())
            {
                pos = null;
            }

            // PORT26: Inventory#getSelected() -> getSelectedItem() (rename in 26.1.2)
            final ItemStack current = mc.player.getInventory().getSelectedItem();
            if (current.getItem() instanceof ISpecialBlockPickItem clickableItem)
            {
                // PORT26: Screen#hasControlDown() static removed -> Minecraft#hasControlDown()
                // (instance method, covers the macOS control quirk)
                final boolean ctrlKey = mc.hasControlDown();
                // PORT26: InteractionResult became a sealed interface — PASS/FAIL are
                // singleton record instances (not enum constants), so "case" labels are
                // impossible; reference equality preserves the old switch semantics exactly.
                final InteractionResult pick = clickableItem.onBlockPick(mc.player, current, pos, ctrlKey);
                if (pick == InteractionResult.PASS)
                {
                    // PORT26: KeyMapping#clickCount is private now — re-inject the click
                    // through the public static KeyMapping#click(InputConstants.Key),
                    // which increments the click count of every mapping bound to that key.
                    KeyMapping.click(mc.options.keyPickItem.getKey());
                }
                else if (pick == InteractionResult.FAIL)
                {
                    // cancelled — swallow the pick
                }
                else
                {
                    new ItemMiddleMouseMessage(pos, ctrlKey).sendToServer();
                }
            }
            else
            {
                // PORT26: KeyMapping#clickCount is private now (see PASS case above)
                KeyMapping.click(mc.options.keyPickItem.getKey());
            }
        }

        for (Iterator<Map.Entry<String, BoxPreviewData>> iterator = RenderingCache.boxRenderingCache.entrySet().iterator(); iterator.hasNext(); )
        {
            final var entry = iterator.next();
            if (entry.getValue().isExpired())
            {
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onMouseWheel(final InputEvent.MouseScrollingEvent event)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (event.isCanceled() || mc.player == null || mc.screen != null || mc.level == null) return;
        if (!mc.player.isShiftKeyDown()) return;

        // PORT26: Inventory#getSelected() -> getSelectedItem() (rename in 26.1.2)
        final ItemStack current = mc.player.getInventory().getSelectedItem();
        if (current.getItem() instanceof IScrollableItem scrollableItem)
        {
            // PORT26: Screen#hasControlDown() static removed -> Minecraft#hasControlDown()
            final boolean ctrlKey = mc.hasControlDown();
            // PORT26: InteractionResult became a sealed interface — see note above.
            final InteractionResult scroll = scrollableItem.onMouseScroll(mc.player, current, event.getScrollDeltaX(), event.getScrollDeltaY(), ctrlKey);
            if (scroll == InteractionResult.PASS)
            {
                // normal scrolling — let vanilla handle it
            }
            else if (scroll == InteractionResult.FAIL)
            {
                event.setCanceled(true);
            }
            else
            {
                event.setCanceled(true);
                new ItemMiddleMouseMessage(event.getScrollDeltaX(), event.getScrollDeltaY(), ctrlKey).sendToServer();
            }
        }
    }

    @SubscribeEvent
    public static void onDisconnect(final LoggingOut event)
    {
        // clear local caches
        WindowExtendedBuildTool.clearStaticData();
    }
}
