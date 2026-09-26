package com.ldtteam.domumornamentum.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * List of possible network targets when sending from client to server.
 */
public interface IServerboundDistributor extends CustomPacketPayload
{
    // PORT26: PacketDistributor#sendToServer moved to the client-only ClientPacketDistributor
    // (same pattern as the already-ported com.ldtteam.common.network.IServerboundDistributor).
    // Only ever invoked from client code — the class reference resolves lazily, so this
    // stays safe on a dedicated server.
    public default void sendToServer()
    {
        ClientPacketDistributor.sendToServer(this);
    }
}
