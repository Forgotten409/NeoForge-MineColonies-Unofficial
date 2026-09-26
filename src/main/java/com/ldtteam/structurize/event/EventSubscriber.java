package com.ldtteam.structurize.event;

import com.ldtteam.structurize.commands.EntryPoint;
import com.ldtteam.structurize.items.AbstractItemWithPosSelector;
import com.ldtteam.structurize.management.Manager;
import com.ldtteam.structurize.util.BlockUtils;
import com.ldtteam.structurize.util.IOPool;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Class with methods for receiving various forge events
 */
public class EventSubscriber
{
    static
    {
        // PORT26: Item#canAttackBlock was removed — the pos-selection start capture moved to
        // a PlayerInteractEvent.LeftClickBlock handler (see AbstractItemWithPosSelector
        // javadoc: cancelling also prevents block breaking, exactly like the old
        // canAttackBlock == false). This class is registered on the game bus from the
        // merged-mod bootstrap, so the static initializer is the earliest safe hook.
        NeoForge.EVENT_BUS.addListener(AbstractItemWithPosSelector::handleLeftClickBlock);
    }

    /**
     * Private constructor to hide implicit public one.
     */
    private EventSubscriber()
    {
        /*
         * Intentionally left empty
         */
    }

    /**
     * Called when world is about to load.
     *
     * @param event event
     */
    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event)
    {
        EntryPoint.register(event.getDispatcher(), event.getCommandSelection());
    }

    @SubscribeEvent
    public static void onWorldTick(final LevelTickEvent.Pre event)
    {
        BlockUtils.checkOrInit();
        if (event.getLevel() instanceof ServerLevel serverLevel)
        {
            Manager.onWorldTick(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(@NotNull final ServerStoppingEvent event)
    {
        IOPool.shutdown();
    }
}
