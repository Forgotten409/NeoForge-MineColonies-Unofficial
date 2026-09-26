package com.minecolonies.core.commands.commandTypes;

import com.minecolonies.api.util.Log;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.player.Player;

/**
 * Interface for commands, uses Mojang's Brigadier command framework, @see <a href=https://github.com/Mojang/brigadier></a> .
 */
public interface IMCCommand
{
    int OP_PERM_LEVEL = 4;

    /**
     * Builds the command, overwrite and add further arguments etc for nonsimple commands. When overwriting this make sure to check the preconditions.
     *
     * @return the built command.
     */
    default LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return newLiteral(getName()).executes(this::checkPreConditionAndExecute);
    }

    /**
     * Helper function for typing.
     *
     * @param name name of the new command.
     * @return the builder.
     */
    static LiteralArgumentBuilder<CommandSourceStack> newLiteral(final String name)
    {
        return LiteralArgumentBuilder.literal(name);
    }

    static <T> RequiredArgumentBuilder<CommandSourceStack, T> newArgument(final String name, final ArgumentType<T> type)
    {
        return RequiredArgumentBuilder.argument(name, type);
    }

    /**
     * Executes pre-checks before issuing the command
     *
     * @param context the context.
     * @return 1 if successful and 0 if incomplete.
     */
    default int checkPreConditionAndExecute(final CommandContext<CommandSourceStack> context)
    {
        return checkPreConditionAndExecute(context, this::onExecute);
    }

    /**
     * Executes pre-checks before issuing the command
     *
     * @param context   the context.
     * @param onExecute the execution method.
     * @return 1 if successful and 0 if incomplete.
     */
    default int checkPreConditionAndExecute(final CommandContext<CommandSourceStack> context, final ExecutionHandler onExecute)
    {
        try
        {
            if (!checkPreCondition(context))
            {
                return 0;
            }

            return onExecute.apply(context);
        }
        catch (Throwable e)
        {
            Log.getLogger().warn("Error during running command:", e);
        }

        return 0;
    }

    /**
     * Preconditions to check before executing
     *
     * @param context the command context.
     * @return true if fine.
     */
    default boolean checkPreCondition(final CommandContext<CommandSourceStack> context)
    {
        return context.getSource().getEntity() instanceof Player || checkPermission(context);
    }

    /**
     * PORT26: CommandSourceStack#hasPermission(int) is gone in 26.1 — permission checks go
     * through the PermissionSet / Permission.HasCommandLevel pair now.
     *
     * @param context the command context.
     * @return true if the source has the OP permission level.
     */
    default boolean checkPermission(final CommandContext<CommandSourceStack> context)
    {
        return context.getSource().permissions()
                 .hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(OP_PERM_LEVEL)));
    }

    /**
     * What happens when the command is executed after preConditions are successful.
     *
     * @param context the context of the command execution
     * @return 1 if successful and 0 if incomplete.
     */
    int onExecute(final CommandContext<CommandSourceStack> context);

    /**
     * Name string of the command.
     *
     * @return the name.
     */
    String getName();

    static boolean isPlayerOped(final Player player)
    {
        // PORT26: Player#getServer() is gone (reach the server through the level) and
        // PlayerList#isOp now takes a NameAndId instead of a GameProfile.
        final MinecraftServer server = player.level().getServer();
        if (server == null)
        {
            return false;
        }

        return server.getPlayerList().isOp(new NameAndId(player.getGameProfile()));
    }

    @FunctionalInterface
    interface ExecutionHandler
    {
        Integer apply(CommandContext<CommandSourceStack> t) throws Exception;
    }
}
