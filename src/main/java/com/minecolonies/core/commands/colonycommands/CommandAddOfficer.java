package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.util.constant.translation.CommandTranslationConstants;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCColonyOfficerCommand;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;

import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;
import static com.minecolonies.core.commands.CommandArgumentNames.PLAYERNAME_ARG;

public class CommandAddOfficer implements IMCColonyOfficerCommand
{
    /**
     * What happens when the command is executed after preConditions are successful.
     *
     * @param context the context of the command execution
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        if (!checkPermission(context) && !MineColonies.getConfig().getServer().canPlayerUseAddOfficerCommand.get())
        {
            context.getSource().sendSuccess(() -> Component.translatableEscape(CommandTranslationConstants.COMMAND_DISABLED_IN_CONFIG), true);
            return 0;
        }

        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);

        GameProfile profile;
        try
        {
            // PORT26: getGameProfiles returns NameAndId records — rebuild the GameProfile.
            final NameAndId selected = GameProfileArgument.getGameProfiles(context, PLAYERNAME_ARG).stream().findFirst().orElse(null);
            profile = selected == null ? null : new GameProfile(selected.id(), selected.name());
        }
        catch (CommandSyntaxException e)
        {
            return 0;
        }

        // PORT26: GameProfile is a record now — getId()/getName() → id()/name().
        if (context.getSource().getServer().getPlayerList().getPlayer(profile.id()) == null)
        {
            // could not find player with given name.
            context.getSource().sendSuccess(() -> Component.translatableEscape(CommandTranslationConstants.COMMAND_PLAYER_NOT_FOUND, profile.name()), true);
            return 0;
        }
        colony.getPermissions().addPlayer(profile, colony.getPermissions().getRank(colony.getPermissions().OFFICER_RANK_ID));
        colony.getPackageManager().addImportantColonyPlayer(context.getSource().getServer().getPlayerList().getPlayer(profile.id()));

        context.getSource().sendSuccess(() -> Component.translatableEscape(CommandTranslationConstants.COMMAND_OFFICER_ADD_SUCCESS, profile.name(), colony.getName()), true);
        return 1;
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "addOfficer";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id())
                         .then(IMCCommand.newArgument(PLAYERNAME_ARG, GameProfileArgument.gameProfile()).executes(this::checkPreConditionAndExecute)));
    }
}
