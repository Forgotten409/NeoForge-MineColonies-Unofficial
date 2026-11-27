package com.minecolonies.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only helpers for ColonyView.
 * <p>
 * PORT26: upstream marked the client-side parts of ColonyView with @OnlyIn(Dist.CLIENT). NeoForge 26.1 no longer
 * strips @OnlyIn members, so ColonyView#getWorld kept {@code final Level level = Minecraft.getInstance().level;}.
 * The dedicated server loads ColonyView to call the static ColonyView#serializeNetworkData (from
 * ColonyPackageManager#sendColonyViewPackets), and bytecode verification of getWorld then has to load ClientLevel
 * (to check it is assignable to Level), which throws NoClassDefFoundError as soon as a player joins near a colony.
 * Keeping every client-type reference in this separate class means it is only loaded when called on the client.
 */
public final class ColonyViewClientHelper
{
    private ColonyViewClientHelper()
    {
    }

    /**
     * @return the client level, or null if none is loaded.
     */
    @Nullable
    public static Level getClientLevel()
    {
        return Minecraft.getInstance().level;
    }

    /**
     * @return the local player's block position, or null if there is no local player.
     */
    @Nullable
    public static BlockPos getLocalPlayerPos()
    {
        return Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.blockPosition();
    }

    /**
     * @return true if the client is connected to a remote (dedicated or LAN) server rather than its own integrated one.
     */
    public static boolean isRemoteServer()
    {
        return Minecraft.getInstance().getSingleplayerServer() == null;
    }
}
