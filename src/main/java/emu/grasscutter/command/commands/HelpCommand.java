package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandMap;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import java.util.List;
import java.util.Locale;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(label = "help", targetRequirement = Command.TargetRequirement.NONE)
public final class HelpCommand implements CommandHandler {
    private static final AttributedStyle COMMAND_STYLE =
            AttributedStyle.BOLD.foreground(AttributedStyle.CYAN);
    private static final AttributedStyle LABEL_STYLE =
            AttributedStyle.BOLD.foreground(AttributedStyle.YELLOW);
    private static final AttributedStyle ALIAS_STYLE = AttributedStyle.DEFAULT.faint();
    private static final CommandLine.Help.ColorScheme HELP_COLOR_SCHEME =
            new CommandLine.Help.ColorScheme.Builder(CommandLine.Help.Ansi.ON).build();

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender));
    }

    @CommandLine.Command(name = "help")
    private final class Args implements java.util.concurrent.Callable<Integer> {
        private final Player player;

        @Parameters(index = "0..*", arity = "0..*", paramLabel = "[command [subcommand...]]")
        private List<String> commandPath = List.of();

        private Args(Player player) {
            this.player = player;
        }

        @Override
        public Integer call() {
            Account account = player == null ? null : player.getAccount();
            var commandMap = CommandMap.getInstance();

            if (commandPath.isEmpty()) {
                var visibleHandlers = commandMap.getHandlers().entrySet().stream()
                        .filter(entry -> isVisible(player, account, entry.getValue()))
                        .toList();

                var heading = new AttributedStringBuilder();
                heading.style(COMMAND_STYLE).append(
                        translate(player, "commands.help.available_commands"));
                CommandOutput.sendAttributedMessage(player, heading.toAttributedString());

                int labelWidth = visibleHandlers.stream()
                        .mapToInt(entry -> entry.getKey().length())
                        .max()
                        .orElse(0);
                for (var entry : visibleHandlers) {
                    String label = entry.getKey();
                    CommandHandler handler = entry.getValue();
                    var line = new AttributedStringBuilder();
                    line.append("  ");
                    line.style(COMMAND_STYLE).append(label);
                    line.style(AttributedStyle.DEFAULT)
                            .append(" ".repeat(labelWidth - label.length() + 2))
                            .append(singleLine(handler.getDescriptionString(player)));

                    String[] aliases = handler.getClass().getAnnotation(Command.class).aliases();
                    if (aliases.length > 0) {
                        line.style(ALIAS_STYLE)
                                .append("  [")
                                .append(String.join(", ", aliases))
                                .append("]");
                    }
                    CommandOutput.sendAttributedMessage(player, line.toAttributedString());
                }
                return 0;
            }

            CommandHandler handler = commandMap.getHandler(commandPath.getFirst());
            if (handler == null) {
                CommandOutput.sendTranslatedMessage(player, "commands.generic.command_exist_error");
                CommandOutput.sendMessage(player, "Command: " + commandPath.getFirst());
                return 1;
            }
            if (!isVisible(player, account, handler)) {
                CommandOutput.sendTranslatedMessage(player, "commands.generic.permission_error");
                return 1;
            }

            CommandLine root = handler.createCommandLine(player, null);
            configureUsageColors(root);
            CommandLine selected = findSubcommand(root, commandPath.subList(1, commandPath.size()));
            if (selected == null) {
                CommandOutput.sendMessage(player, "Unknown subcommand: " + commandPath.getLast());
                CommandOutput.sendMessage(player, root.getUsageMessage().stripTrailing());
                return 1;
            }
            describe(player, handler, selected);
            return 0;
        }
    }

    static CommandLine findSubcommand(CommandLine root, List<String> names) {
        CommandLine current = root;
        for (String name : names) {
            current = current.getSubcommands().get(name.toLowerCase(Locale.ROOT));
            if (current == null) return null;
        }
        return current;
    }

    private static void configureUsageColors(CommandLine commandLine) {
        commandLine.setColorScheme(HELP_COLOR_SCHEME);
        commandLine.getSubcommands().values().forEach(HelpCommand::configureUsageColors);
    }

    private static boolean isVisible(Player player, Account account, CommandHandler handler) {
        String permission = handler.getClass().getAnnotation(Command.class).permission();
        return player == null || account != null && account.hasPermission(permission);
    }

    private static String singleLine(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").strip();
    }

    private static void describe(Player player, CommandHandler handler, CommandLine commandLine) {
        Command metadata = handler.getClass().getAnnotation(Command.class);

        var header = new AttributedStringBuilder();
        header.style(COMMAND_STYLE).append(handler.getLabel());
        header.style(AttributedStyle.DEFAULT)
                .append("  ")
                .append(singleLine(handler.getDescriptionString(player)));
        CommandOutput.sendAttributedMessage(player, header.toAttributedString());

        // Picocli owns the usage layout and wrapping. Do not add a second indentation layer here.
        CommandOutput.sendMessage(player, commandLine.getUsageMessage().stripTrailing());

        var details = new AttributedStringBuilder();
        details.append("\n");
        if (metadata.aliases().length > 0) {
            details.style(LABEL_STYLE).append(translate(player, "commands.help.aliases"));
            details.style(AttributedStyle.DEFAULT).append(String.join(", ", metadata.aliases()));
            details.append("\n");
        }

        details.style(LABEL_STYLE).append(translate(player, "commands.help.tip_need_permission"));
        if (metadata.permission().isEmpty()) {
            details.style(AttributedStyle.DEFAULT)
                    .append(translate(player, "commands.help.tip_need_no_permission"));
        } else {
            details.style(AttributedStyle.DEFAULT).append(metadata.permission());
        }
        if (!metadata.permissionTargeted().isEmpty()) {
            details.style(ALIAS_STYLE).append(" ").append(translate(
                    player, "commands.help.tip_permission_targeted", metadata.permissionTargeted()));
        }
        CommandOutput.sendAttributedMessage(player, details.toAttributedString());
    }
}
