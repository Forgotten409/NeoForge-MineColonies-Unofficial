package com.ldtteam.domumornamentum.client.event.handlers;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.client.color.MateriallyTexturedBlockTintSource;
import com.ldtteam.domumornamentum.client.model.MateriallyTexturedBlockStateModel;
import com.ldtteam.domumornamentum.client.model.MateriallyTexturedItemModel;
import com.ldtteam.domumornamentum.client.model.RetexturedQuadBuilder;
import com.ldtteam.domumornamentum.client.screens.ArchitectsCutterScreen;
import com.ldtteam.domumornamentum.container.ModContainerTypes;
import com.ldtteam.domumornamentum.util.Constants;
import net.minecraft.client.renderer.item.ItemModel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Client mod-bus event handlers — PORT 26.1.2 rewrite.
 *
 * <p>1.21.1 registered: item model overrides via {@code ItemProperties} (system removed in
 * 1.21.4+ — replaced by data-driven item models), render layers via
 * {@code ItemBlockRenderTypes.setRenderLayer} (removed — render types are now per-quad in
 * {@code BakedQuad.MaterialInfo} / {@code render_type} in model JSON), and the
 * {@code materially_textured} geometry loader (replaced by a custom block state model type +
 * custom item model type).
 *
 * <p>Registered manually from {@code DomumOrnamentum.init} (merged single-mod port —
 * annotation scanning by modid would not find these).
 */
public class ModBusEventHandler
{
    @SubscribeEvent
    public static void onMenuScreensRegistry(final RegisterMenuScreensEvent event)
    {
        event.register(ModContainerTypes.ARCHITECTS_CUTTER.get(), ArchitectsCutterScreen::new);
    }

    /**
     * Registers the custom block state model type ({@code domum_ornamentum:materially_textured})
     * used by materially textured blockstates.
     */
    @SubscribeEvent
    public static void onRegisterBlockStateModels(final RegisterBlockStateModels event)
    {
        event.registerModel(Constants.resLocDO("materially_textured"), MateriallyTexturedBlockStateModel.Unbaked.CODEC);
    }

    /**
     * Registers the custom item model type ({@code domum_ornamentum:materially_textured}) used by
     * materially textured item models.
     */
    @SubscribeEvent
    public static void onRegisterItemModels(final RegisterItemModelsEvent event)
    {
        event.register(Constants.resLocDO("materially_textured"), MateriallyTexturedItemModel.Unbaked.CODEC);
    }

    /**
     * Registers the tint sources for all materially texturable blocks — one layer list per block,
     * each layer decoding {@code componentIndex << 3 | targetTintIndex} (see
     * {@link RetexturedQuadBuilder#encodeTintIndex}).
     */
    @SubscribeEvent
    public static void onRegisterBlockTintSources(final RegisterColorHandlersEvent.BlockTintSources event)
    {
        final List<net.minecraft.client.color.block.BlockTintSource> layers = new ArrayList<>(RetexturedQuadBuilder.TINT_LAYERS);
        for (int i = 0; i < RetexturedQuadBuilder.TINT_LAYERS; i++)
        {
            layers.add(new MateriallyTexturedBlockTintSource(i));
        }
        event.register(layers, ModBlocks.getMateriallyTexturableBlocks());
    }

    @SubscribeEvent
    public static void onFMLClientSetup(final FMLClientSetupEvent event)
    {
        // PORT26: ItemProperties.register + ItemBlockRenderTypes.setRenderLayer removed in 1.21.4+.
        // Replacements:
        //  - door/trapdoor/post model overrides -> data-driven item models (datagen)
        //  - render layers -> "render_type" field in model JSON (datagen)
    }
}
