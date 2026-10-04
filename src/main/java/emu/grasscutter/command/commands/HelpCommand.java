package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandMap;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(label = "help", targetRequirement = Command.TargetRequirement.NONE)
public final class HelpCommand implements CommandHandler {
    private static final boolean SHOW_COMMANDS_WITHOUT_PERMISSIONS = false;

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender));
    }

    @CommandLine.Command(name = "help")
    private final class Args implements Runnable {
        private final Player player;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[command]")
        private String commandName;

        private Args(Player player) {
            this.player = player;
        }

        @Override
        public void run() {
            Account account = player == null ? null : player.getAccount();
            var commandMap = CommandMap.getInstance();
            List<String> commands = new ArrayList<>();
            List<String> denied = new ArrayList<>();

            if (commandName == null) {
                commandMap
                        .getHandlers()
                        .forEach(
                                (label, handler) ->
                                        addVisibleCommand(player, account, handler, commands, denied));
                CommandOutput.sendTranslatedMessage(player, "commands.help.available_commands");
            } else {
                CommandHandler handler = commandMap.getHandler(commandName);
                if (handler == null) {
                    CommandOutput.sendTranslatedMessage(player, "commands.generic.command_exist_error");
                    CommandOutput.sendMessage(player, "Command: " + commandName.toLowerCase());
                    return;
                }
                addVisibleCommand(player, account, handler, commands, denied);
            }

            String suffix = "\n\t" + translate(player, "commands.help.warn_player_has_no_permission");
            commands.forEach(text -> CommandOutput.sendMessage(player, text));
            denied.forEach(text -> CommandOutput.sendMessage(player, text + suffix));
        }
    }

    private static void addVisibleCommand(
            Player player,
            Account account,
            CommandHandler handler,
            List<String> commands,
            List<String> denied) {
        Command metadata = handler.getClass().getAnnotation(Command.class);
        boolean allowed = player == null || account.hasPermission(metadata.permission());
        if (allowed) {
            commands.add(describe(player, handler));
        } else if (SHOW_COMMANDS_WITHOUT_PERMISSIONS) {
            denied.add(describe(player, handler));
        }
    }

    private static String describe(Player player, CommandHandler handler) {
        Command metadata = handler.getClass().getAnnotation(Command.class);
        StringBuilder builder =
                new StringBuilder(handler.getLabel())
                        .append(" - ")
                        .append(handler.getDescriptionString(player))
                        .append("\n\t")
                        .append(handler.getUsageString(player));

        if (metadata.aliases().length > 0) {
            builder.append("\n\t").append(translate(player, "commands.help.aliases"));
            for (String alias : metadata.aliases()) {
                builder.append(alias).append(' ');
            }
        }

        builder.append("\n\t").append(translate(player, "commands.help.tip_need_permission"));
        if (metadata.permission().isEmpty()) {
            builder.append(translate(player, "commands.help.tip_need_no_permission"));
        } else {
            builder.append(metadata.permission());
        }
        if (!metadata.permissionTargeted().isEmpty()) {
            builder.append(' ')
                    .append(
                            translate(
                                    player,
                                    "commands.help.tip_permission_targeted",
                                    metadata.permissionTargeted()));
        }
        return builder.toString();
    }
}
