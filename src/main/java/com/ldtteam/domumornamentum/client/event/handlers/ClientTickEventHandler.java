package com.ldtteam.domumornamentum.client.event.handlers;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * PORT26 (batch 10): converted from static listener + {@code register(Class)} to an INSTANCE
 * listener registered via {@code NeoForge.EVENT_BUS.register(getInstance())}.
 *
 * <p>Reason: user reports showed every event-driven behaviour of this handler frozen (preview
 * cycling stuck on one material — see {@code MaterialTextureDataUtil} which no longer depends on
 * this counter). The mod-bus class registration demonstrably works, but the game-bus static
 * registration was suspect. Instance-object registration is the most battle-tested path.
 *
 * <p>No {@code @EventBusSubscriber} annotation: the merged mod id is {@code minecolonies}, so
 * annotation scanning by {@code domum_ornamentum} would never find this class anyway.
 */
public class ClientTickEventHandler
{
    private static final ClientTickEventHandler INSTANCE = new ClientTickEventHandler();

    public static ClientTickEventHandler getInstance()
    {
        return INSTANCE;
    }

    private long clientTicks = 0;
    private long nonePausedTicks = 0;

    private ClientTickEventHandler()
    {
    }

    @SubscribeEvent
    public void onTickClientTick(final ClientTickEvent.Pre event)
    {
        ClientTickEventHandler.getInstance().onClientTick();
    }

    private void onClientTick()
    {
        clientTicks++;
        if (!Minecraft.getInstance().isPaused()) {
            nonePausedTicks++;
        }
    }

    public long getClientTicks()
    {
        return clientTicks;
    }

    public long getNonePausedTicks()
    {
        return nonePausedTicks;
    }
}
