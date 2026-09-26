package com.minecolonies.core.event;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.datalistener.*;
import com.minecolonies.core.entity.pathfinding.Pathfinding;
import com.minecolonies.core.util.BackUpHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Event handler used to catch various forge events.
 */
public class FMLEventHandler
{
    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Pre event)
    {
        IColonyManager.getInstance().onServerTick(event);
        DataPackSyncEventHandler.ServerEvents.load(event.getServer());
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Pre event)
    {
        IColonyManager.getInstance().onClientTick(event);
    }

    @SubscribeEvent
    public static void onPlayerLogin(@NotNull final PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer)
        {
            // This automatically reloads the owner of the colony if failed.
            IColonyManager.getInstance().getIColonyByOwner(event.getEntity().level(), event.getEntity());
            //ColonyManager.syncAllColoniesAchievements();
        }
    }

    @SubscribeEvent
    public static void onAddReloadListenerEvent(@NotNull final AddServerReloadListenersEvent event)
    {
        // PORT26: AddReloadListenerEvent -> AddServerReloadListenersEvent; listeners need a key now.
        final String ns = com.minecolonies.api.util.constant.Constants.MOD_ID;
        event.addListener(Identifier.fromNamespaceAndPath(ns, "crafter_recipes"), new CrafterRecipeListener());
        event.addListener(Identifier.fromNamespaceAndPath(ns, "research"), new ResearchListener());
        event.addListener(Identifier.fromNamespaceAndPath(ns, "custom_visitors"), new CustomVisitorListener());
        event.addListener(Identifier.fromNamespaceAndPath(ns, "citizen_names"), new CitizenNameListener());
        event.addListener(Identifier.fromNamespaceAndPath(ns, "quests"), new QuestJsonListener());
        event.addListener(Identifier.fromNamespaceAndPath(ns, "item_nbt"), new ItemNbtListener());
        event.addListener(Identifier.fromNamespaceAndPath(ns, "study_items"), StudyItemListener.INSTANCE);
        event.addListener(Identifier.fromNamespaceAndPath(ns, "diseases"), new DiseasesListener());
        event.addListener(Identifier.fromNamespaceAndPath(ns, "recruitment_items"), new RecruitmentItemsListener());
    }

    @SubscribeEvent
    public static void onServerStarted(@NotNull final ServerStartedEvent event)
    {
        // PORT26 (crash fix #6): equipment tier registration needs new ItemStack(...).getMaxDamage();
        // the item holders' data-driven components are only bound during registry-data load
        // (between AddServerReloadListenersEvent and listener apply — both before this event),
        // so this is the earliest safe point. It used to run in FMLCommonSetupEvent (1.21.1:
        // components lived on the Item instance; 26.1.2: NPE "Components not bound yet").
        // Guarded — a failure degrades to missing durability tiers instead of crashing startup.
        try
        {
            com.minecolonies.api.equipment.ModEquipmentTypes.initRegisterEquipmentTiers();
        }
        catch (final Exception e)
        {
            com.minecolonies.api.util.Log.getLogger().error("Failed to register equipment tiers", e);
        }
        BackUpHelper.loadMissingColonies();
    }

    @SubscribeEvent
    public static void onWorldTick(final LevelTickEvent.Pre event)
    {
        IColonyManager.getInstance().onWorldTick(event);
    }

    @SubscribeEvent
    public static void onServerAboutToStart(@NotNull final ServerAboutToStartEvent event)
    {
        IColonyManager.getInstance().getRecipeManager().reset();
    }

    @SubscribeEvent
    public static void onServerStopped(@NotNull final ServerStoppingEvent event)
    {
        Pathfinding.shutdown();
        DataPackSyncEventHandler.ServerEvents.reset();
    }
}
