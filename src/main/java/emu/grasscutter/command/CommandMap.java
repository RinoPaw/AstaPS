package emu.grasscutter.command;

import static emu.grasscutter.config.Configuration.SERVER;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.event.game.ExecuteCommandEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
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

    private final Map<String, CommandHandler> commands = new TreeMap<>();
    private final Map<String, CommandHandler> aliases = new TreeMap<>();
    private final Map<String, Command> annotations = new TreeMap<>();
    private final ConcurrentMap<String, Integer> targetPlayerIds = new ConcurrentHashMap<>();
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

    static CommandLine createRootCommandLine() {
        var root = new CommandLine(CommandSpec.create().name("astaps"));
        // @UID is AstaPS targeting syntax, never a picocli argument file.
        root.setExpandAtFiles(false);
        return root;
    }

    static List<String> parseCommandTokens(String rawMessage) throws SyntaxError {
        return new ArrayList<>(
                COMMAND_PARSER
                        .parse(rawMessage, rawMessage.length(), Parser.ParseContext.ACCEPT_LINE)
                        .words());
    }

    static String normalizeTargetSelector(String selector) {
        return selector.startsWith("@") ? selector.substring(1) : selector;
    }

    static String takeInlineTargetSelector(List<String> args, boolean inlineTarget) {
        if (!inlineTarget) return null;

        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.startsWith("@")) {
                return args.remove(i).substring(1);
            }
        }
        return null;
    }

    static void executeCommand(Runnable runnable, boolean threaded, Executor executor) {
        if (threaded) executor.execute(runnable);
        else runnable.run();
    }

    private static String normalizeCommandName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Command names must not be blank.");
        }
        return name.toLowerCase(Locale.ROOT);
    }

    private static String[] normalizeAliases(Command annotation) {
        String[] declaredAliases = annotation.aliases();
        String[] normalizedAliases = new String[declaredAliases.length];
        for (int i = 0; i < declaredAliases.length; i++) {
            normalizedAliases[i] = normalizeCommandName(declaredAliases[i]);
        }
        return normalizedAliases;
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

    private static CommandLine configureCommandLine(
            CommandLine cli, Player sender, CommandHandler handler) {
        cli.setExpandAtFiles(false);
        cli.setParameterExceptionHandler(
                (exception, argv) -> {
                    Throwable cause = exception.getCause();
                    if (cause instanceof CommandLine.TypeConversionException conversion
                            && conversion.getMessage() != null
                            && !conversion.getMessage().isBlank()) {
                        CommandOutput.sendMessage(sender, conversion.getMessage());
                    } else {
                        String message = exception.getMessage();
                        if (message != null && !message.isBlank()) {
                            CommandOutput.sendMessage(sender, message);
                        }
                        CommandOutput.sendMessage(
                                sender, exception.getCommandLine().getUsageMessage().stripTrailing());
                    }
                    return exception.getCommandLine().getCommandSpec().exitCodeOnInvalidInput();
                });
        cli.setExecutionExceptionHandler(
                (exception, commandLine, parseResult) -> {
                    Grasscutter.getLogger()
                            .error("Failed to execute command " + handler.getLabel() + ".", exception);
                    String message = exception.getMessage();
                    CommandOutput.sendMessage(
                            sender,
                            message == null || message.isBlank()
                                    ? "Command execution failed."
                                    : message);
                    return commandLine.getCommandSpec().exitCodeOnExecutionException();
                });
        return cli;
    }

    private static CommandLine createCompletionCommandLine(
            String label, CommandHandler handler) {
        CommandLine child = handler.createCompletionCommandLine();
        child.getCommandSpec().name(label);
        child.setExpandAtFiles(false);
        return child;
    }

    private void rebuildPicocliTree() {
        synchronized (this.picocliLock) {
            var root = createRootCommandLine();

            for (var entry : this.commands.entrySet()) {
                String label = entry.getKey();
                CommandHandler handler = entry.getValue();
                Command annotation = this.annotations.get(label);
                CommandLine child = createCompletionCommandLine(label, handler);
                root.addSubcommand(label, child, normalizeAliases(annotation));
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
                        synchronized (this.picocliLock) {
                            new PicocliJLineCompleter(this.commandLine.getCommandSpec())
                                    .complete(currentReader, parsedLine, candidates);
                        }
                    });
        }
    }

    private String resolveCommandLabel(String label) {
        synchronized (this.picocliLock) {
            var child = this.commandLine.getSubcommands().get(label);
            return child == null ? null : child.getCommandSpec().name();
        }
    }

    private void validateRegistration(String label, Command annotation) {
        String annotationLabel = normalizeCommandName(annotation.label());
        if (!label.equals(annotationLabel)) {
            throw new IllegalArgumentException(
                    "Registered command label '"
                            + label
                            + "' must match @Command label '"
                            + annotationLabel
                            + "'.");
        }

        if (this.commands.containsKey(label) || this.aliases.containsKey(label)) {
            throw new IllegalArgumentException("Command name already registered: " + label);
        }

        var registrationNames = new HashSet<String>();
        registrationNames.add(label);
        for (String alias : annotation.aliases()) {
            String normalized = normalizeCommandName(alias);
            if (!registrationNames.add(normalized)) {
                throw new IllegalArgumentException(
                        "Duplicate command label or alias in registration: " + normalized);
            }
            if (this.commands.containsKey(normalized) || this.aliases.containsKey(normalized)) {
                throw new IllegalArgumentException("Command name already registered: " + normalized);
            }
        }
    }

    private void addRegistration(String label, CommandHandler command, Command annotation) {
        this.annotations.put(label, annotation);
        this.commands.put(label, command);

        for (String alias : annotation.aliases()) {
            String normalized = normalizeCommandName(alias);
            this.aliases.put(normalized, command);
            this.annotations.put(normalized, annotation);
        }
    }

    private void removeRegistration(String label, Command annotation) {
        this.annotations.remove(label);
        this.commands.remove(label);

        for (String alias : annotation.aliases()) {
            String normalized = normalizeCommandName(alias);
            this.aliases.remove(normalized);
            this.annotations.remove(normalized);
        }
    }

    private void removePicocliCommand(String label, Command annotation) {
        CommandSpec root = this.commandLine.getCommandSpec();
        CommandLine removed = root.removeSubcommand(label);
        if (removed == null) {
            throw new IllegalStateException("Picocli command missing from routing tree: " + label);
        }

        for (String alias : normalizeAliases(annotation)) {
            root.removeSubcommand(alias);
        }
    }

    private void restoreRegistry(
            Map<String, CommandHandler> previousCommands,
            Map<String, CommandHandler> previousAliases,
            Map<String, Command> previousAnnotations,
            CommandLine previousTree) {
        this.commands.clear();
        this.commands.putAll(previousCommands);
        this.aliases.clear();
        this.aliases.putAll(previousAliases);
        this.annotations.clear();
        this.annotations.putAll(previousAnnotations);
        this.commandLine = previousTree;
    }

    public CommandMap registerCommand(String label, CommandHandler command) {
        label = normalizeCommandName(label);
        Grasscutter.getLogger().trace("Registered command: " + label);

        synchronized (this.picocliLock) {
            Command annotation = command.getClass().getAnnotation(Command.class);
            if (annotation == null) {
                throw new IllegalArgumentException("Command handler must be annotated with @Command.");
            }

            this.validateRegistration(label, annotation);
            CommandLine child = createCompletionCommandLine(label, command);
            this.commandLine.addSubcommand(label, child, normalizeAliases(annotation));
            this.addRegistration(label, command, annotation);
        }

        return this;
    }

    CommandMap registerCommands(List<? extends CommandHandler> commandHandlers) {
        if (commandHandlers.isEmpty()) return this;

        synchronized (this.picocliLock) {
            var previousCommands = new TreeMap<>(this.commands);
            var previousAliases = new TreeMap<>(this.aliases);
            var previousAnnotations = new TreeMap<>(this.annotations);
            CommandLine previousTree = this.commandLine;

            try {
                for (CommandHandler command : commandHandlers) {
                    Command annotation = command.getClass().getAnnotation(Command.class);
                    if (annotation == null) {
                        throw new IllegalArgumentException(
                                "Command handler must be annotated with @Command.");
                    }

                    String label = normalizeCommandName(annotation.label());
                    Grasscutter.getLogger().trace("Registered command: " + label);
                    this.validateRegistration(label, annotation);
                    this.addRegistration(label, command, annotation);
                }

                this.rebuildPicocliTree();
            } catch (RuntimeException exception) {
                this.restoreRegistry(
                        previousCommands, previousAliases, previousAnnotations, previousTree);
                throw exception;
            }
        }

        return this;
    }

    public CommandMap unregisterCommand(String label) {
        label = normalizeCommandName(label);
        Grasscutter.getLogger().trace("Un-registered command: " + label);

        synchronized (this.picocliLock) {
            CommandHandler handler = this.commands.get(label);
            if (handler == null) return this;

            Command annotation = handler.getClass().getAnnotation(Command.class);
            this.removePicocliCommand(label, annotation);
            this.removeRegistration(label, annotation);
        }
        return this;
    }

    public List<Command> getAnnotationsAsList() {
        synchronized (this.picocliLock) {
            return new ArrayList<>(this.annotations.values());
        }
    }

    public Map<String, Command> getAnnotations() {
        synchronized (this.picocliLock) {
            return new LinkedHashMap<>(this.annotations);
        }
    }

    public List<CommandHandler> getHandlersAsList() {
        synchronized (this.picocliLock) {
            return new ArrayList<>(this.commands.values());
        }
    }

    public Map<String, CommandHandler> getHandlers() {
        synchronized (this.picocliLock) {
            return new LinkedHashMap<>(this.commands);
        }
    }

    public CommandHandler getHandler(String label) {
        String normalized = normalizeCommandName(label);
        synchronized (this.picocliLock) {
            CommandHandler handler = this.commands.get(normalized);
            if (handler == null) handler = this.aliases.get(normalized);
            return handler;
        }
    }

    private Player getTargetPlayer(
            String playerId,
            Player player,
            Player targetPlayer,
            List<String> args,
            boolean inlineTarget) {
        String inlineSelector = takeInlineTargetSelector(args, inlineTarget);
        if (inlineSelector != null) {
            if (inlineSelector.isEmpty()) return null;

            int uid = getUidFromString(inlineSelector);
            if (uid == INVALID_UID) {
                CommandOutput.sendTranslatedMessage(player, "commands.generic.invalid.uid");
                throw new IllegalArgumentException();
            }
            targetPlayer = Grasscutter.getGameServer().getPlayerByUid(uid, true);
            if (targetPlayer == null) {
                CommandOutput.sendTranslatedMessage(player, "commands.execution.player_exist_error");
                throw new IllegalArgumentException();
            }
            return targetPlayer;
        }

        if (targetPlayer != null) return targetPlayer;

        Integer rememberedTargetUid = targetPlayerIds.get(playerId);
        if (rememberedTargetUid != null) {
            targetPlayer = Grasscutter.getGameServer().getPlayerByUid(rememberedTargetUid, true);
            if (targetPlayer == null) {
                CommandOutput.sendTranslatedMessage(player, "commands.execution.player_exist_error");
                throw new IllegalArgumentException();
            }
            return targetPlayer;
        }

        return player;
    }

    private boolean setPlayerTarget(String playerId, Player player, String selector) {
        if (selector.isEmpty()) {
            targetPlayerIds.remove(playerId);
            CommandOutput.sendTranslatedMessage(player, "commands.execution.clear_target");
            return true;
        }

        int uid = getUidFromString(selector);
        if (uid == INVALID_UID) {
            CommandOutput.sendTranslatedMessage(player, "commands.generic.invalid.uid");
            return false;
        }
        Player targetPlayer = Grasscutter.getGameServer().getPlayerByUid(uid, true);
        if (targetPlayer == null) {
            CommandOutput.sendTranslatedMessage(player, "commands.execution.player_exist_error");
            return false;
        }

        targetPlayerIds.put(playerId, uid);
        String target = uid + " (" + targetPlayer.getAccount().getUsername() + ")";
        CommandOutput.sendTranslatedMessage(player, "commands.execution.set_target", target);
        CommandOutput.sendTranslatedMessage(
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
            CommandOutput.sendTranslatedMessage(player, "commands.generic.not_specified");
            return;
        }

        final List<String> tokens;
        try {
            tokens = parseCommandTokens(rawMessage);
        } catch (SyntaxError error) {
            CommandOutput.sendMessage(player, error.getMessage());
            return;
        }
        if (tokens.isEmpty()) return;

        String rawLabel = tokens.remove(0);
        String label = rawLabel.toLowerCase(Locale.ROOT);
        List<String> args = tokens;
        String playerId = (player == null) ? CONSOLE_ID : player.getAccount().getId();

        if (rawLabel.startsWith("@")) {
            this.setPlayerTarget(playerId, player, rawLabel.substring(1));
            return;
        }
        if (label.equals("target")) {
            if (!args.isEmpty()) {
                this.setPlayerTarget(playerId, player, normalizeTargetSelector(args.get(0)));
            } else {
                this.setPlayerTarget(playerId, player, "");
            }
            return;
        }

        String resolvedLabel = this.resolveCommandLabel(label);
        if (resolvedLabel == null) {
            CommandOutput.sendTranslatedMessage(player, "commands.generic.unknown_command", label);
            return;
        }

        final CommandHandler handler;
        final Command annotation;
        synchronized (this.picocliLock) {
            handler = this.commands.get(resolvedLabel);
            annotation = this.annotations.get(resolvedLabel);
        }
        if (handler == null || annotation == null) {
            CommandOutput.sendTranslatedMessage(player, "commands.generic.unknown_command", label);
            return;
        }

        try {
            targetPlayer =
                    getTargetPlayer(
                            playerId, player, targetPlayer, args, annotation.inlineTarget());
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
                CommandOutput.sendTranslatedMessage(player, "commands.execution.need_target");
                return;
            }
            if (targetRequirement == Command.TargetRequirement.ONLINE && !targetPlayer.isOnline()) {
                handler.sendUsageMessage(player);
                CommandOutput.sendTranslatedMessage(player, "commands.execution.need_target_online");
                return;
            }
            if (targetRequirement == Command.TargetRequirement.OFFLINE && targetPlayer.isOnline()) {
                handler.sendUsageMessage(player);
                CommandOutput.sendTranslatedMessage(player, "commands.execution.need_target_offline");
                return;
            }
        }

        final Player sender = player;
        final Player target = targetPlayer;
        final String[] commandArgs = args.toArray(String[]::new);
        Runnable runnable = () -> {
            CommandLine cli = configureCommandLine(handler.createCommandLine(sender, target), sender, handler);
            cli.execute(commandArgs);
        };

        executeCommand(runnable, annotation.threading(), Grasscutter.getThreadPool());
    }

    private void scan() {
        Reflections reflector = Grasscutter.reflector;
        Set<Class<?>> classes = reflector.getTypesAnnotatedWith(Command.class);
        var handlers = new ArrayList<CommandHandler>(classes.size());

        for (Class<?> annotated : classes) {
            try {
                Object object = annotated.getDeclaredConstructor().newInstance();
                if (object instanceof CommandHandler handler) {
                    handlers.add(handler);
                } else {
                    Grasscutter.getLogger()
                            .error("Class " + annotated.getName() + " is not a CommandHandler!");
                }
            } catch (Exception exception) {
                Grasscutter.getLogger()
                        .error(
                                "Failed to instantiate command handler for "
                                        + annotated.getSimpleName(),
                                exception);
            }
        }

        this.registerCommands(handlers);
    }
}
