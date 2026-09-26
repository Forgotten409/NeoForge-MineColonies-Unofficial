package com.ldtteam.structurize.event;

import com.ldtteam.structurize.blockentities.ModBlockEntities;
import com.ldtteam.structurize.blocks.ModBlocks;
import com.ldtteam.structurize.client.*;
import com.ldtteam.structurize.api.Log;
import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.client.model.OverlaidModelLoader;
import com.ldtteam.structurize.items.ItemStackTooltip;
import com.ldtteam.structurize.items.ModItems;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers.ContainerPlacementHandler;
import com.ldtteam.structurize.storage.ClientStructurePackLoader;
import com.ldtteam.structurize.util.WorldRenderMacros;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

public class ClientLifecycleSubscriber
{
    @SubscribeEvent
    public static void onClientInit(final FMLClientSetupEvent event)
    {
        ClientStructurePackLoader.onClientLoading();
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(final AddClientReloadListenersEvent event)
    {
        // PORT26: RegisterClientReloadListenersEvent#registerReloadListener(PreparableReloadListener)
        // became AddClientReloadListenersEvent#addListener(Identifier, PreparableReloadListener)
        // (same adaptation as the blockui port).
        event.addListener(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "blueprint_renderer_cache"), new SimplePreparableReloadListener<>()
        {
            @Override
            protected Object prepare(final ResourceManager manager, final ProfilerFiller profiler)
            {
                return new Object();
            }

            @Override
            protected void apply(final Object source, final ResourceManager manager, final ProfilerFiller profiler)
            {
                Log.getLogger().debug("Clearing blueprint renderer cache.");
                BlueprintHandler.getInstance().clearCache();
            }
        });
    }

    // PORT26: @OnlyIn removed — NeoForge 26.1 no longer strips members at runtime and logs a
    // startup warning (loadwarning.neoforge.onlyin) for every @OnlyIn use in mod code.
    @SubscribeEvent
    public static void doClientStuff(final EntityRenderersEvent.RegisterRenderers event)
    {
        // PORT26: ItemBlockRenderTypes#setRenderLayer was removed in 1.21.4+ — render types
        // are data-driven now ("render_type" in the block's model JSON, see the domum-ornamentum
        // port note). The blockSubstitution translucent layer must come from assets.
    }

    @SubscribeEvent
    public static void registerGeometry(final ModelEvent.RegisterLoaders event)
    {
        // PORT26: ModelEvent.RegisterGeometryLoaders was renamed to ModelEvent.RegisterLoaders
        // (register(Identifier, UnbakedModelLoader) signature unchanged).
        event.register(Constants.resLocStruct("overlaid"), new OverlaidModelLoader());
    }

    @SubscribeEvent
    public static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(ModBlockEntities.TAG_SUBSTITUTION.get(), TagSubstitutionRenderer::new);
    }

    @SubscribeEvent
    public static void registerTooltips(final RegisterClientTooltipComponentFactoriesEvent event)
    {
        event.register(ItemStackTooltip.class, ClientItemStackTooltip::new);
    }

    @SubscribeEvent
    public static void registerKeys(final RegisterKeyMappingsEvent event)
    {
        ModKeyMappings.register(event);
    }

    @SubscribeEvent
    public static void registerGlobablRenderBuffers(final RegisterRenderBuffersEvent event)
    {
        WorldRenderMacros.RenderTypes.registerBuffer(event);
        BlueprintRenderer.BlueprintRenderTypes.registerBuffer(event);
    }

    /**
     * PORT26: custom render pipelines must be registered on the mod bus before first use —
     * the line/box pipelines from WorldRenderMacros and the blueprint ghost pipeline from
     * the blueprint renderer.
     */
    @SubscribeEvent
    public static void registerRenderPipelines(final RegisterRenderPipelinesEvent event)
    {
        WorldRenderMacros.RenderTypes.registerPipelines(event);
        BlueprintRenderer.BlueprintRenderTypes.registerPipelines(event);
    }

    // PORT26: the 1.21.1 registerClientExtensions handler was dropped —
    // IClientItemExtensions#getCustomRenderer() and BlockEntityWithoutLevelRenderer no longer
    // exist (custom item rendering became data-driven). The TagSubstitutionRenderer item
    // rendering hookup has to be reworked in the client/ batch (S5) that ports that renderer.
    //
    // Also dropped unused 1.21.1 imports that no longer compile:
    // net.neoforged.neoforge.capabilities.{RegisterCapabilitiesEvent, Capabilities.ItemHandler}
    // (IItemHandler became ResourceHandler<ItemResource> + Capabilities.Item.* — none used here).
}
