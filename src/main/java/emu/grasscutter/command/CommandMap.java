package emu.grasscutter.command;

import static emu.grasscutter.config.Configuration.SERVER;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.event.game.ExecuteCommandEvent;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jline.reader.Parser;
import org.jline.reader.SyntaxError;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.LineReaderImpl;
import org.reflections.Reflections;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.shell.jline3.PicocliJLineCompleter;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public final class CommandMap {
    private static final int INVALID_UID = Integer.MIN_VALUE;
    private static final String CONSOLE_ID = "console";
    private static final Parser COMMAND_PARSER = new DefaultParser();

    private final Map<String, PicocliCommandHandler> commands = new TreeMap<>();
    private final Map<String, PicocliCommandHandler> aliases = new TreeMap<>();
    private final Map<String, Command> annotations = new TreeMap<>();
    private final Object2IntMap<String> targetPlayerIds = new Object2IntOpenHashMap<>();
    private final Object picocliLock = new Object();

    private volatile CommandLine commandLine = createRootCommandLine();

    public CommandMap() {
        this(false);
    }

    public CommandMap(boolean scan) {
        if (scan) this.scan();
        this.installConsoleCompleter();
    }

    public static CommandMap getInstance() {
        return Grasscutter.getCommandMap();
    }

    public CommandLine getCommandLine() {
        return this.commandLine;
    }

    private static CommandLine createRootCommandLine() {
        var root = new CommandLine(CommandSpec.create().name("astaps"));
        // @UID is AstaPS targeting syntax, never a picocli argument file.
        root.setExpandAtFiles(false);
        return root;
    }

    private static int getUidFromString(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ignored) {
            var account = DatabaseHelper.getAccountByName(input);
            if (account == null) return INVALID_UID;
            var player = DatabaseHelper.getPlayerByAccount(account, Player.class);
            if (player == null) return INVALID_UID;
            return player.getUid();
        }
    }

    private void rebuildPicocliTree() {
        synchronized (this.picocliLock) {
            var root = createRootCommandLine();

            for (var entry : this.commands.entrySet()) {
                String label = entry.getKey();
                PicocliCommandHandler handler = entry.getValue();
                CommandLine child = handler.createCompletionCommandLine();
                child.getCommandSpec().name(label);
                child.setExpandAtFiles(false);

                String[] effectiveAliases =
                        this.aliases.entrySet().stream()
                                .filter(alias -> alias.getValue() == handler)
                                .filter(alias -> alias.getKey().equals(alias.getKey().toLowerCase()))
                                .filter(alias -> !this.commands.containsKey(alias.getKey()))
                                .map(Map.Entry::getKey)
                                .toArray(String[]::new);

                root.addSubcommand(label, child, effectiveAliases);
            }

            root.setExpandAtFiles(false);
            this.commandLine = root;
        }
    }

    private void installConsoleCompleter() {
        var reader = Grasscutter.getConsole();
        if (reader instanceof LineReaderImpl lineReader) {
            lineReader.setCompleter(
                    (currentReader, parsedLine, candidates) -> {
                        CommandLine cli = this.commandLine;
                        new PicocliJLineCompleter(cli.getCommandSpec())
                                .complete(currentReader, parsedLine, candidates);
                    });
        }
    }

    private String resolveCommandLabel(String label) {
        synchronized (this.picocliLock) {
            var child = this.commandLine.getSubcommands().get(label);
            return child == null ? null : child.getCommandSpec().name();
        }
    }

    public CommandMap registerCommand(String label, PicocliCommandHandler command) {
        Grasscutter.getLogger().trace("Registered command: " + label);
        label = label.toLowerCase();

        Command annotation = command.getClass().getAnnotation(Command.class);
        this.annotations.put(label, annotation);
        this.commands.put(label, command);

        for (String alias : annotation.aliases()) {
            String normalized = alias.toLowerCase();
            this.aliases.put(normalized, command);
            this.annotations.put(normalized, annotation);
        }

        this.rebuildPicocliTree();
        return this;
    }

    public CommandMap unregisterCommand(String label) {
        Grasscutter.getLogger().trace("Un-registered command: " + label);
        label = label.toLowerCase();

        PicocliCommandHandler handler = this.commands.get(label);
        if (handler == null) return this;

        Command annotation = handler.getClass().getAnnotation(Command.class);
        this.annotations.remove(label);
        this.commands.remove(label);

        for (String alias : annotation.aliases()) {
            String normalized = alias.toLowerCase();
            this.aliases.remove(normalized);
            this.annotations.remove(normalized);
        }

        this.rebuildPicocliTree();
        return this;
    }

    public List<Command> getAnnotationsAsList() {
        return new ArrayList<>(this.annotations.values());
    }

    public Map<String, Command> getAnnotations() {
        return new LinkedHashMap<>(this.annotations);
    }

    public List<PicocliCommandHandler> getHandlersAsList() {
        return new ArrayList<>(this.commands.values());
    }

    public Map<String, PicocliCommandHandler> getHandlers() {
        return this.commands;
    }

    public PicocliCommandHandler getHandler(String label) {
        String normalized = label.toLowerCase();
        PicocliCommandHandler handler = this.commands.get(normalized);
        if (handler == null) handler = this.aliases.get(normalized);
        return handler;
    }

    private Player getTargetPlayer(
            String playerId, Player player, Player targetPlayer, List<String> args) {
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.startsWith("@")) {
                arg = args.remove(i).substring(1);
                if (arg.isEmpty()) return null;

                int uid = getUidFromString(arg);
                if (uid == INVALID_UID) {
                    CommandHandler.sendTranslatedMessage(player, "commands.generic.invalid.uid");
                    throw new IllegalArgumentException();
                }
                targetPlayer = Grasscutter.getGameServer().getPlayerByUid(uid, true);
                if (targetPlayer == null) {
                    CommandHandler.sendTranslatedMessage(player, "commands.execution.player_exist_error");
                    throw new IllegalArgumentException();
                }
                return targetPlayer;
            }
        }

        if (targetPlayer != null) return targetPlayer;

        if (targetPlayerIds.containsKey(playerId)) {
            targetPlayer =
                    Grasscutter.getGameServer().getPlayerByUid(targetPlayerIds.getInt(playerId), true);
            if (targetPlayer == null) {
                CommandHandler.sendTranslatedMessage(player, "commands.execution.player_exist_error");
                throw new IllegalArgumentException();
            }
            return targetPlayer;
        }

        return player;
    }

    private boolean setPlayerTarget(String playerId, Player player, String targetUid) {
        if (targetUid.isEmpty()) {
            targetPlayerIds.removeInt(playerId);
            CommandHandler.sendTranslatedMessage(player, "commands.execution.clear_target");
            return true;
        }

        int uid = getUidFromString(targetUid);
        if (uid == INVALID_UID) {
            CommandHandler.sendTranslatedMessage(player, "commands.generic.invalid.uid");
            return false;
        }
        Player targetPlayer = Grasscutter.getGameServer().getPlayerByUid(uid, true);
        if (targetPlayer == null) {
            CommandHandler.sendTranslatedMessage(player, "commands.execution.player_exist_error");
            return false;
        }

        targetPlayerIds.put(playerId, uid);
        String target = uid + " (" + targetPlayer.getAccount().getUsername() + ")";
        CommandHandler.sendTranslatedMessage(player, "commands.execution.set_target", target);
        CommandHandler.sendTranslatedMessage(
                player,
                targetPlayer.isOnline()
                        ? "commands.execution.set_target_online"
                        : "commands.execution.set_target_offline",
                target);
        return true;
    }

    public void invoke(Player player, Player targetPlayer, String rawMessage) {
        var event = new ExecuteCommandEvent(player, targetPlayer, rawMessage);
        if (!event.call()) return;

        player = event.getSender();
        targetPlayer = event.getTarget();
        rawMessage = event.getCommand();

        if (SERVER.logCommands) {
            if (player != null) {
                Grasscutter.getLogger()
                        .info(
                                "Command used by ["
                                        + player.getAccount().getUsername()
                                        + " (Player UID: "
                                        + player.getUid()
                                        + ")]: "
                                        + rawMessage);
            } else {
                Grasscutter.getLogger().info("Command used by server console: " + rawMessage);
            }
        }

        rawMessage = rawMessage.trim();
        if (rawMessage.isEmpty()) {
            CommandHandler.sendTranslatedMessage(player, "commands.generic.not_specified");
            return;
        }

        final List<String> tokens;
        try {
            tokens = new ArrayList<>(
                    COMMAND_PARSER
                            .parse(rawMessage, rawMessage.length(), Parser.ParseContext.ACCEPT_LINE)
                            .words());
        } catch (SyntaxError error) {
            CommandHandler.sendMessage(player, error.getMessage());
            return;
        }
        if (tokens.isEmpty()) return;

        String label = tokens.remove(0).toLowerCase();
        List<String> args = tokens;
        String playerId = (player == null) ? CONSOLE_ID : player.getAccount().getId();

        if (label.startsWith("@")) {
            this.setPlayerTarget(playerId, player, label.substring(1));
            return;
        }
        if (label.equals("target")) {
            if (!args.isEmpty()) {
                String targetUid = args.get(0);
                if (targetUid.startsWith("@")) targetUid = targetUid.substring(1);
                this.setPlayerTarget(playerId, player, targetUid);
            } else {
                this.setPlayerTarget(playerId, player, "");
            }
            return;
        }

        String resolvedLabel = this.resolveCommandLabel(label);
        if (resolvedLabel == null) {
            CommandHandler.sendTranslatedMessage(player, "commands.generic.unknown_command", label);
            return;
        }

        PicocliCommandHandler handler = this.commands.get(resolvedLabel);
        Command annotation = this.annotations.get(resolvedLabel);
        if (handler == null || annotation == null) {
            CommandHandler.sendTranslatedMessage(player, "commands.generic.unknown_command", label);
            return;
        }

        try {
            targetPlayer = getTargetPlayer(playerId, player, targetPlayer, args);
        } catch (IllegalArgumentException e) {
            return;
        }

        if (!Grasscutter.getPermissionHandler()
                .checkPermission(
                        player,
                        targetPlayer,
                        annotation.permission(),
                        annotation.permissionTargeted())) {
            return;
        }

        Command.TargetRequirement targetRequirement = annotation.targetRequirement();
        if (targetRequirement != Command.TargetRequirement.NONE) {
            if (targetPlayer == null) {
                handler.sendUsageMessage(player);
                CommandHandler.sendTranslatedMessage(player, "commands.execution.need_target");
                return;
            }
            if (targetRequirement == Command.TargetRequirement.ONLINE && !targetPlayer.isOnline()) {
                handler.sendUsageMessage(player);
                CommandHandler.sendTranslatedMessage(player, "commands.execution.need_target_online");
                return;
            }
            if (targetRequirement == Command.TargetRequirement.OFFLINE && targetPlayer.isOnline()) {
                handler.sendUsageMessage(player);
                CommandHandler.sendTranslatedMessage(player, "commands.execution.need_target_offline");
                return;
            }
        }

        final Player sender = player;
        final Player target = targetPlayer;
        final String[] commandArgs = args.toArray(String[]::new);
        Runnable runnable = () -> {
            CommandLine cli = handler.createCommandLine(sender, target);
            cli.setExpandAtFiles(false);
            cli.execute(commandArgs);
        };

        if (annotation.threading()) new Thread(runnable).start();
        else runnable.run();
    }

    private void scan() {
        Reflections reflector = Grasscutter.reflector;
        Set<Class<?>> classes = reflector.getTypesAnnotatedWith(Command.class);

        classes.forEach(
                annotated -> {
                    try {
                        Command metadata = annotated.getAnnotation(Command.class);
                        Object object = annotated.getDeclaredConstructor().newInstance();
                        if (object instanceof PicocliCommandHandler handler) {
                            this.registerCommand(metadata.label(), handler);
                        } else {
                            Grasscutter.getLogger()
                                    .error("Class " + annotated.getName() + " is not a PicocliCommandHandler!");
                        }
                    } catch (Exception exception) {
                        Grasscutter.getLogger()
                                .error(
                                        "Failed to register command handler for " + annotated.getSimpleName(),
                                        exception);
                    }
                });
    }
}
