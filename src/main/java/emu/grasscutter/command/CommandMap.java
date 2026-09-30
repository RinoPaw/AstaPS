package emu.grasscutter.command;

import static emu.grasscutter.config.Configuration.SERVER;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.event.game.ExecuteCommandEvent;
import it.unimi.dsi.fastutil.objects.*;
import java.util.*;
import org.jline.reader.impl.LineReaderImpl;
import org.reflections.Reflections;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.shell.jline3.PicocliJLineCompleter;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public final class CommandMap {
    private static final int INVALID_UID = Integer.MIN_VALUE;
    private static final String consoleId = "console";

    private final Map<String, CommandHandler> commands = new TreeMap<>();
    private final Map<String, CommandHandler> aliases = new TreeMap<>();
    private final Map<String, Command> annotations = new TreeMap<>();
    private final Object2IntMap<String> targetPlayerIds = new Object2IntOpenHashMap<>();
    private final Object picocliLock = new Object();

    /** Picocli owns command/alias parsing and the command model used by JLine completion. */
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
        // AstaPS owns @UID targeting. Picocli must never reinterpret @foo as an argument file.
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

    /**
     * Rebuilds the picocli command tree from the public Grasscutter command registry.
     *
     * <p>Legacy handlers get a lossless catch-all positional grammar. Handlers that implement
     * {@link PicocliCommandHandler} expose their real nested grammar to the same tree, so JLine
     * completion and runtime parsing advance together during migration.
     */
    private void rebuildPicocliTree() {
        synchronized (this.picocliLock) {
            var root = createRootCommandLine();

            for (var entry : this.commands.entrySet()) {
                String label = entry.getKey();
                CommandHandler handler = entry.getValue();
                CommandLine child;

                if (handler instanceof PicocliCommandHandler nativeHandler) {
                    child = nativeHandler.createCompletionCommandLine();
                    child.getCommandSpec().name(label);
                    child.setExpandAtFiles(false);
                } else {
                    var childSpec = CommandSpec.create().name(label);
                    childSpec.addPositional(
                            PositionalParamSpec.builder()
                                    .index("0..*")
                                    .arity("0..*")
                                    .type(String[].class)
                                    .paramLabel("ARG")
                                    .build());
                    child = new CommandLine(childSpec);
                    child.setUnmatchedOptionsArePositionalParams(true);
                    child.setExpandAtFiles(false);
                }

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

    /**
     * Resolve a top-level command or alias through picocli's registered command model.
     *
     * <p>This deliberately does not call {@code parseArgs}: native command roots may have required
     * positional parameters, and top-level routing happens before those payload tokens are parsed.
     */
    private String resolveCommandLabel(String label) {
        synchronized (this.picocliLock) {
            var child = this.commandLine.getSubcommands().get(label);
            return child == null ? null : child.getCommandSpec().name();
        }
    }

    /** Register a command handler. */
    public CommandMap registerCommand(String label, CommandHandler command) {
        Grasscutter.getLogger().trace("Registered command: " + label);
        label = label.toLowerCase();

        Command annotation = command.getClass().getAnnotation(Command.class);
        this.annotations.put(label, annotation);
        this.commands.put(label, command);

        for (String alias : annotation.aliases()) {
            this.aliases.put(alias, command);
            this.annotations.put(alias, annotation);
        }

        this.rebuildPicocliTree();
        return this;
    }

    /** Removes a registered command handler. */
    public CommandMap unregisterCommand(String label) {
        Grasscutter.getLogger().trace("Un-registered command: " + label);

        CommandHandler handler = this.commands.get(label);
        if (handler == null) return this;

        Command annotation = handler.getClass().getAnnotation(Command.class);
        this.annotations.remove(label);
        this.commands.remove(label);

        for (String alias : annotation.aliases()) {
            this.aliases.remove(alias);
            this.annotations.remove(alias);
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

    public List<CommandHandler> getHandlersAsList() {
        return new ArrayList<>(this.commands.values());
    }

    public Map<String, CommandHandler> getHandlers() {
        return this.commands;
    }

    public CommandHandler getHandler(String label) {
        CommandHandler handler = this.commands.get(label);
        if (handler == null) handler = this.aliases.get(label);
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

    /** Invoke a command handler with the given raw message. */
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

        // Preserve historical tokenization during the compatibility migration.
        String[] split = rawMessage.split(" ");
        String label = split[0].toLowerCase();
        List<String> args = new ArrayList<>(Arrays.asList(split).subList(1, split.length));
        String playerId = (player == null) ? consoleId : player.getAccount().getId();

        // Preserve AstaPS global target syntax outside the per-command grammar.
        if (label.startsWith("@")) {
            this.setPlayerTarget(playerId, player, label.substring(1));
            return;
        } else if (label.equalsIgnoreCase("target")) {
            if (!args.isEmpty()) {
                String targetUidStr = args.get(0);
                if (targetUidStr.startsWith("@")) targetUidStr = targetUidStr.substring(1);
                this.setPlayerTarget(playerId, player, targetUidStr);
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

        CommandHandler handler = this.commands.get(resolvedLabel);
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

            if ((targetRequirement == Command.TargetRequirement.ONLINE) && !targetPlayer.isOnline()) {
                handler.sendUsageMessage(player);
                CommandHandler.sendTranslatedMessage(player, "commands.execution.need_target_online");
                return;
            }

            if ((targetRequirement == Command.TargetRequirement.OFFLINE) && targetPlayer.isOnline()) {
                handler.sendUsageMessage(player);
                CommandHandler.sendTranslatedMessage(player, "commands.execution.need_target_offline");
                return;
            }
        }

        final var playerF = player;
        final var targetPlayerF = targetPlayer;
        final var handlerF = handler;

        Runnable runnable = () -> handlerF.execute(playerF, targetPlayerF, args);
        if (annotation.threading()) new Thread(runnable).start();
        else runnable.run();
    }

    /** Scans for all classes annotated with {@link Command} and registers them. */
    private void scan() {
        Reflections reflector = Grasscutter.reflector;
        Set<Class<?>> classes = reflector.getTypesAnnotatedWith(Command.class);

        classes.forEach(
                annotated -> {
                    try {
                        Command cmdData = annotated.getAnnotation(Command.class);
                        Object object = annotated.getDeclaredConstructor().newInstance();
                        if (object instanceof CommandHandler)
                            this.registerCommand(cmdData.label(), (CommandHandler) object);
                        else
                            Grasscutter.getLogger()
                                    .error("Class " + annotated.getName() + " is not a CommandHandler!");
                    } catch (Exception exception) {
                        Grasscutter.getLogger()
                                .error(
                                        "Failed to register command handler for " + annotated.getSimpleName(),
                                        exception);
                    }
                });
    }
}
