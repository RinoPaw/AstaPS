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

    /**
     * Picocli owns command-name/alias parsing and the command model used by JLine completion.
     *
     * <p>The built-in handlers still receive their historical {@code List<String>} argument payload
     * during the compatibility migration. This keeps every existing command spelling and compact DSL
     * intact while individual handlers are moved to typed picocli parameters.
     */
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
            // We will be immediately fetching the player again after this,
            // but offline vs online Player safety is more important than saving a lookup
            return player.getUid();
        }
    }

    /**
     * Rebuilds the picocli command tree from the public Grasscutter command registry.
     *
     * <p>This also keeps legacy plugin registration working: plugins can continue using
     * {@link #registerCommand(String, CommandHandler)} and the new parser/completer sees the change
     * immediately.
     */
    private void rebuildPicocliTree() {
        synchronized (this.picocliLock) {
            var root = createRootCommandLine();

            for (var entry : this.commands.entrySet()) {
                String label = entry.getKey();
                CommandHandler handler = entry.getValue();

                var childSpec = CommandSpec.create().name(label);
                childSpec.addPositional(
                        PositionalParamSpec.builder()
                                .index("0..*")
                                .arity("0..*")
                                .type(String[].class)
                                .paramLabel("ARG")
                                .build());
                var child = new CommandLine(childSpec);

                // Existing commands use compact tokens and negative numbers that can look like
                // options. Until each handler has a typed schema, every historical token is a
                // positional argument.
                child.setUnmatchedOptionsArePositionalParams(true);
                child.setExpandAtFiles(false);

                String[] effectiveAliases =
                        this.aliases.entrySet().stream()
                                .filter(alias -> alias.getValue() == handler)
                                // Invocation lower-cases the command token today, so a mixed-case
                                // alias is historically unreachable. Do not accidentally add syntax.
                                .filter(alias -> alias.getKey().equals(alias.getKey().toLowerCase()))
                                // A real command label has always won over an alias in getHandler().
                                .filter(alias -> !this.commands.containsKey(alias.getKey()))
                                .map(Map.Entry::getKey)
                                .toArray(String[]::new);

                root.addSubcommand(label, child, effectiveAliases);
            }

            // Apply these parser settings after adding children so picocli propagates them through
            // the complete hierarchy.
            root.setUnmatchedOptionsArePositionalParams(true);
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
     * Resolve the first token through picocli and return the canonical registered command label.
     *
     * <p>Only command/alias selection is authoritative in this compatibility phase. Handler payload
     * tokens remain untouched and are forwarded exactly as the old command system produced them.
     */
    private String resolveCommandLabel(String label) {
        synchronized (this.picocliLock) {
            try {
                var result = this.commandLine.parseArgs(label);
                var subcommand = result.subcommand();
                return subcommand == null ? null : subcommand.commandSpec().name();
            } catch (CommandLine.ParameterException ignored) {
                return null;
            }
        }
    }

    /**
     * Register a command handler.
     *
     * @param label The command label.
     * @param command The command handler.
     * @return Instance chaining.
     */
    public CommandMap registerCommand(String label, CommandHandler command) {
        Grasscutter.getLogger().trace("Registered command: " + label);
        label = label.toLowerCase();

        // Get command data.
        Command annotation = command.getClass().getAnnotation(Command.class);
        this.annotations.put(label, annotation);
        this.commands.put(label, command);

        // Register aliases.
        for (String alias : annotation.aliases()) {
            this.aliases.put(alias, command);
            this.annotations.put(alias, annotation);
        }

        this.rebuildPicocliTree();
        return this;
    }

    /**
     * Removes a registered command handler.
     *
     * @param label The command label.
     * @return Instance chaining.
     */
    public CommandMap unregisterCommand(String label) {
        Grasscutter.getLogger().trace("Un-registered command: " + label);

        CommandHandler handler = this.commands.get(label);
        if (handler == null) return this;

        Command annotation = handler.getClass().getAnnotation(Command.class);
        this.annotations.remove(label);
        this.commands.remove(label);

        // Unregister aliases.
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

    /**
     * Returns a list of all registered commands.
     *
     * @return All command handlers as a list.
     */
    public List<CommandHandler> getHandlersAsList() {
        return new ArrayList<>(this.commands.values());
    }

    public Map<String, CommandHandler> getHandlers() {
        return this.commands;
    }

    /**
     * Returns a handler by label/alias.
     *
     * @param label The command label.
     * @return The command handler.
     */
    public CommandHandler getHandler(String label) {
        CommandHandler handler = this.commands.get(label);
        if (handler == null) {
            // Try getting by alias
            handler = this.aliases.get(label);
        }
        return handler;
    }

    private Player getTargetPlayer(
            String playerId, Player player, Player targetPlayer, List<String> args) {
        // Top priority: If any @UID argument is present, override targetPlayer with it.
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.startsWith("@")) {
                arg = args.remove(i).substring(1);
                if (arg.isEmpty()) {
                    // This is a special case to target nothing, distinct from failing to assign a target.
                    // This is specifically to allow in-game players to run a command without targeting
                    // themselves or anyone else.
                    return null;
                }
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

        // Next priority: If we invoked with a target, use that.
        // By default, this only happens when you message another player in-game with a command.
        if (targetPlayer != null) {
            return targetPlayer;
        }

        // Next priority: Use previously-set target. (see /target [[@]UID])
        if (targetPlayerIds.containsKey(playerId)) {
            targetPlayer =
                    Grasscutter.getGameServer().getPlayerByUid(targetPlayerIds.getInt(playerId), true);
            // We check every time in case the target is deleted after being targeted
            if (targetPlayer == null) {
                CommandHandler.sendTranslatedMessage(player, "commands.execution.player_exist_error");
                throw new IllegalArgumentException();
            }
            return targetPlayer;
        }

        // Lowest priority: Target the player invoking the command. In the case of the console, this
        // will return null.
        return player;
    }

    private boolean setPlayerTarget(String playerId, Player player, String targetUid) {
        if (targetUid.isEmpty()) { // Clears the default targetPlayer.
            targetPlayerIds.removeInt(playerId);
            CommandHandler.sendTranslatedMessage(player, "commands.execution.clear_target");
            return true;
        }

        // Sets default targetPlayer to the UID provided.
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

    /**
     * Invoke a command handler with the given arguments.
     *
     * @param player The player invoking the command or null for the server console.
     * @param rawMessage The messaged used to invoke the command.
     */
    public void invoke(Player player, Player targetPlayer, String rawMessage) {
        // Invoke the ExecuteCommandEvent.
        var event = new ExecuteCommandEvent(player, targetPlayer, rawMessage);
        if (!event.call()) return;

        player = event.getSender();
        targetPlayer = event.getTarget();
        rawMessage = event.getCommand();

        // The console outputs in-game command. [{Account Username} (Player UID: {Player Uid})]
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

        // Keep historical tokenization for the compatibility migration. JLine/picocli now own the
        // command model and top-level routing; quoting semantics can be changed separately later.
        String[] split = rawMessage.split(" ");
        String label = split[0].toLowerCase();
        List<String> args = new ArrayList<>(Arrays.asList(split).subList(1, split.length));
        String playerId = (player == null) ? consoleId : player.getAccount().getId();

        // Check for special cases - currently only target command.
        if (label.startsWith("@")) { // @[UID]
            this.setPlayerTarget(playerId, player, label.substring(1));
            return;
        } else if (label.equalsIgnoreCase("target")) { // target [[@]UID]
            if (!args.isEmpty()) {
                String targetUidStr = args.get(0);
                if (targetUidStr.startsWith("@")) {
                    targetUidStr = targetUidStr.substring(1);
                }
                this.setPlayerTarget(playerId, player, targetUidStr);
            } else {
                this.setPlayerTarget(playerId, player, "");
            }
            return;
        }

        // Picocli is authoritative for command/alias selection.
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

        // Resolve 'targetPlayer'.
        try {
            targetPlayer = getTargetPlayer(playerId, player, targetPlayer, args);
        } catch (IllegalArgumentException e) {
            return;
        }

        // Check for permissions.
        if (!Grasscutter.getPermissionHandler()
                .checkPermission(
                        player,
                        targetPlayer,
                        annotation.permission(),
                        annotation.permissionTargeted())) {
            return;
        }

        // Check if command has unfulfilled constraints on targetPlayer
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

        // Copy player and handler to final properties.
        final var playerF = player;
        final var targetPlayerF = targetPlayer;
        final var handlerF = handler;

        // Invoke execute method for handler.
        Runnable runnable = () -> handlerF.execute(playerF, targetPlayerF, args);
        if (annotation.threading()) {
            new Thread(runnable).start();
        } else {
            runnable.run();
        }
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
