package com.ldtteam.blockui.mod;

import com.ldtteam.blockui.AtlasManager;
import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.hooks.HookManager;
import com.ldtteam.blockui.hooks.HookRegistries;
import com.ldtteam.blockui.mod.container.ContainerHook;
import com.ldtteam.blockui.util.resloc.OutOfJarResourceLocation;
import com.ldtteam.blockui.views.BOWindow;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.minecraft.util.profiling.Profiler;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.function.Consumer;

public class ClientEventSubscriber
{
    /**
     * Used to catch the clientTickEvent.
     * Call renderer cache cleaning every 5 secs (100 ticks).
     *
     * @param event the catched event.
     */
    @SubscribeEvent
    public static void onClientTickStart(final ClientTickEvent.Pre event)
    {
        if (Minecraft.getInstance().hasAltDown() && Minecraft.getInstance().hasControlDown() && Minecraft.getInstance().hasShiftDown())
        {
            // PORT26: InputConstants#isKeyDown now takes the Window object itself
            // (com.mojang.blaze3d.platform.Window), not the raw long handle.
            if (InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_X))
            {
                final BOWindow window = new BOWindow();
                int id = 0;

                final Button dumpAtlases = createTestGuiButton(id++, "Dump mod atlases to run folder", null);
                dumpAtlases.setHandler(b -> {
                    final Path dumpingFolder = Path.of("atlas_dump").toAbsolutePath().normalize();
                    // PORT26: player is null at the main menu — guard before chatting.
                    if (Minecraft.getInstance().player != null)
                    {
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("Dumping atlases into: " + dumpingFolder));
                    }
                    AtlasManager.INSTANCE.dumpAtlases(dumpingFolder);
                });
                window.addChild(dumpAtlases);

                window.addChild(createTestGuiButton(id++, "General All-in-one", Identifier.fromNamespaceAndPath(BlockUI.MOD_ID, "gui/test.xml"), parent -> {
                    parent.findPaneOfTypeByID("missing_out_of_jar", Image.class).setImage(OutOfJarResourceLocation.ofMinecraftFolder(BlockUI.MOD_ID, "missing_out_of_jar.png"), false);
                    parent.findPaneOfTypeByID("working_out_of_jar", Image.class).setImage(OutOfJarResourceLocation.of(BlockUI.MOD_ID, Path.of("../../src/test/resources/button.png")), false);
                    // PORT26: skin future may complete with null (skin still loading or
                    // selector picked a missing cape/elytra) — guard all three so the XML
                    // placeholder texture is kept instead of clearing the image source (which
                    // crashed rendering in dev with "Missing image source: player_skin").
                    OutOfJarResourceLocation.ofMinecraftSkin(Minecraft.getInstance(), Minecraft.getInstance().getGameProfile(), null)
                        .thenAccept(resLoc -> {if (resLoc != null){parent.findPaneOfTypeByID("player_skin", Image.class).setImage(resLoc, false);}});
                    OutOfJarResourceLocation.ofMinecraftSkin(Minecraft.getInstance(), Minecraft.getInstance().getGameProfile(), PlayerSkin::cape)
                        .thenAccept(resLoc -> {if (resLoc != null){parent.findPaneOfTypeByID("player_cape", Image.class).setImage(resLoc, false);}});
                    OutOfJarResourceLocation.ofMinecraftSkin(Minecraft.getInstance(), Minecraft.getInstance().getGameProfile(), PlayerSkin::elytra)
                        .thenAccept(resLoc -> {if (resLoc != null){parent.findPaneOfTypeByID("player_elytra", Image.class).setImage(resLoc, false);}});
                }));
                window.addChild(createTestGuiButton(id++, "Tooltip Positioning", Identifier.fromNamespaceAndPath(BlockUI.MOD_ID, "gui/test2.xml")));
                window.addChild(createTestGuiButton(id++, "ItemIcon To BlockState", Identifier.fromNamespaceAndPath(BlockUI.MOD_ID, "gui/test3.xml"), BlockStateTestGui::setup));
                window.addChild(createTestGuiButton(id++, "Scrolling Lists", Identifier.fromNamespaceAndPath(BlockUI.MOD_ID, "gui/test4.xml"), ScrollingListsGui::setup));

                final Text builderTest = new Text();
                builderTest.setSize(ButtonImage.DEFAULT_BUTTON_WIDTH * 2 + 20, ButtonImage.DEFAULT_BUTTON_HEIGHT);
                builderTest.setPosition(0, ((id + 1) / 2) * (builderTest.getHeight() + 10));
                // PORT26: merged single-mod port — the mod file is registered as "minecolonies".
                // A lookup by "blockui" returns null here and would NPE on versionString().
                final var modFile = ModList.get().getModFileById("minecolonies");
                final String modVersion = modFile != null ? modFile.versionString() : "unknown";
                PaneBuilders.textBuilder()
                    .append(Component.literal(BlockUI.MOD_ID))
                    .append(Component.literal(" - "))
                    .append(Component.literal(modVersion))
                    .paragraphBreak()
                    .colorName("red")
                    .underlined()
                    .append(Component.translatable("blockui.tooltip.item_additional_info",
                        Component.translatable("key.keyboard.left.control")
                            .append(" + ")
                            .append(Component.translatable("key.keyboard.left.shift"))
                            .append(" + ")
                            .append(Component.translatable("key.keyboard.left.alt"))
                            .setStyle(Style.EMPTY.withItalic(true))))
                    .applyToPane(builderTest);
                window.addChild(builderTest);

                window.open();
            }
        }
    }

    @SubscribeEvent
    public static void onClientTickEnd(final ClientTickEvent.Post event)
    {
        if (Minecraft.getInstance().level != null)
        {
            // PORT26: Minecraft#getProfiler() was removed with the profiler rework —
            // use the thread-local static Profiler#get() (same pattern as Minecraft itself).
            Profiler.get().push("hook_manager_tick");
            HookRegistries.tick(Minecraft.getInstance().level.getGameTime());
            Profiler.get().pop();
        }
    }

    @SafeVarargs
    private static Button createTestGuiButton(final int order,
        final String name,
        final Identifier testGuiResLoc,
        final Consumer<BOWindow>... setups)
    {
        final Button button = new ButtonImage(true);
        button.setPosition((order % 2) * (button.getWidth() + 20), (order / 2) * (button.getHeight() + 10));
        button.setText(Component.literal(name));
        button.setHandler(b -> {
            new BOWindow(testGuiResLoc)
            {
                @Override
                public void onOpened()
                {
                    super.onOpened();
                    for (final Consumer<BOWindow> setup : setups)
                    {
                        setup.accept(this);
                    }
                }
            }.openAsLayer();
        });
        return button;
    }

    /**
     * Used to catch the scroll when no gui is open.
     *
     * @param event the catched event.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScrollEvent(final MouseScrollingEvent event)
    {
        // cancel in-game scrolling when raytraced gui has scrolling list
        event.setCanceled(HookManager.onScroll(event.getScrollDeltaX(), event.getScrollDeltaY()));
    }

    /**
     * Hook test container gui.
     */
    @SubscribeEvent
    public static void onTagsUpdated(final TagsUpdatedEvent event)
    {
        ContainerHook.init();
    }

    @SubscribeEvent
    public static void renderOverlay(final RenderGuiLayerEvent.Pre event)
    {
        if (Minecraft.getInstance().screen instanceof BOScreen && event.getName().equals(VanillaGuiLayers.CROSSHAIR))
        {
            event.setCanceled(true);
        }
    }
}
