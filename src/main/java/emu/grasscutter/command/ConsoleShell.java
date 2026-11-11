package emu.grasscutter.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jline.reader.Completer;
import org.jline.reader.SyntaxError;
import org.jline.shell.CommandDescription;
import org.jline.shell.CommandDispatcher;
import org.jline.shell.CommandGroup;
import org.jline.shell.CommandLine;
import org.jline.shell.CommandSession;
import org.jline.shell.Pipeline;
import org.jline.shell.Shell;
import org.jline.shell.UnknownCommandException;
import org.jline.shell.impl.AbstractCommand;
import org.jline.shell.impl.CommandHighlighter;
import org.jline.shell.impl.DefaultCommandDispatcher;
import org.jline.shell.impl.PipelineParser;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStyle;

/** Adapts the game's command registry and results to the native JLine 4 shell. */
public final class ConsoleShell {
    private ConsoleShell() {}

    public static Shell create(CommandMap commands, Terminal terminal) throws IOException {
        var dispatcher = new DefaultCommandDispatcher(terminal, null, pipelineParser(), null) {
            @Override
            public org.jline.shell.Command findCommand(String name) {
                var command = super.findCommand(name.toLowerCase(Locale.ROOT));
                // Unknown commands must produce a failed result so `unknown || help` can continue.
                return command != null ? command : wrap(commands, name);
            }

            @Override
            public void trace(Throwable failure) {
                // CommandMap already delivered business feedback to the stage output.
                if (failure instanceof FailedCommand) return;
                if (failure instanceof UnknownCommandException || failure instanceof SyntaxError) {
                    terminal.writer().println(failure.getMessage());
                } else {
                    failure.printStackTrace(terminal.writer());
                }
                terminal.flush();
            }
        };

        CommandGroup group = new CommandGroup() {
            @Override
            public String name() {
                return "astaps";
            }

            @Override
            public Collection<org.jline.shell.Command> commands() {
                List<org.jline.shell.Command> entries = new ArrayList<>();
                for (Map.Entry<String, CommandHandler> entry :
                        commands.getHandlers().entrySet()) {
                    var metadata = entry.getValue().getClass()
                            .getAnnotation(emu.grasscutter.command.Command.class);
                    String[] aliases = java.util.Arrays.stream(metadata.aliases())
                            .map(alias -> alias.toLowerCase(Locale.ROOT))
                            .toArray(String[]::new);
                    entries.add(wrap(commands, entry.getKey(), aliases));
                }
                entries.add(wrap(commands, "target"));
                return entries;
            }

            @Override
            public org.jline.shell.Command command(String name) {
                // Bare @UID selectors also change the remembered console target.
                if (CommandMap.isTargetSelector(name)) return wrap(commands, name);
                return CommandGroup.super.command(name.toLowerCase(Locale.ROOT));
            }
        };

        return Shell.builder()
                .terminal(terminal)
                .dispatcher(dispatcher)
                .groups(group)
                .prompt(commands::getConsolePrompt)
                .historyCommands(true)
                .highlighter(new CommandHighlighter(highlighterView(dispatcher)))
                .onReaderReady((reader, d) -> commands.installConsoleCompleter(reader))
                .build();
    }

    /**
     * The execution dispatcher intentionally resolves unknown names to wrappers so the shell can
     * propagate their failure status through `||`. The highlighter needs the real registry view,
     * otherwise every unknown command would be colored as a known command.
     */
    private static CommandDispatcher highlighterView(CommandDispatcher delegate) {
        return new CommandDispatcher() {
            @Override
            public org.jline.shell.Command findCommand(String name) {
                String normalized = name.toLowerCase(Locale.ROOT);
                boolean known = normalized.equals("exit")
                        || normalized.equals("history")
                        || delegate.groups().stream()
                                .anyMatch(group -> group.command(normalized) != null);
                return known ? delegate.findCommand(normalized) : null;
            }

            @Override
            public void addGroup(CommandGroup group) {
                delegate.addGroup(group);
            }

            @Override
            public List<CommandGroup> groups() {
                return delegate.groups();
            }

            @Override
            public Object execute(String command) throws Exception {
                return delegate.execute(command);
            }

            @Override
            public Object execute(Pipeline pipeline) throws Exception {
                return delegate.execute(pipeline);
            }

            @Override
            public Completer completer() {
                return delegate.completer();
            }

            @Override
            public CommandDescription describe(CommandLine commandLine) {
                return delegate.describe(commandLine);
            }

            @Override
            public Terminal terminal() {
                return delegate.terminal();
            }

            @Override
            public void initialize(File directory) throws Exception {
                delegate.initialize(directory);
            }

            @Override
            public void cleanUp() {
                delegate.cleanUp();
            }

            @Override
            public AttributedStyle errorStyle() {
                return delegate.errorStyle();
            }

            @Override
            public void trace(Throwable failure) {
                delegate.trace(failure);
            }

            @Override
            public void close() {
                delegate.close();
            }
        };
    }

    static PipelineParser pipelineParser() {
        // Concurrent pipelines, file redirection and background jobs await execution-boundary decisions.
        return new PipelineParser() {
            @Override
            protected String matchOperator(String line, int pos) {
                String symbol = super.matchOperator(line, pos);
                if (symbol == null) return null;
                Pipeline.Operator op = Pipeline.Operator.fromSymbol(symbol);
                return switch (op) {
                    case PIPE, FLIP, REDIRECT, APPEND, INPUT_REDIRECT, HEREDOC,
                            STDERR_REDIRECT, COMBINED_REDIRECT -> null;
                    default -> symbol;
                };
            }
        };
    }

    private static org.jline.shell.Command wrap(CommandMap commands, String name, String... aliases) {
        return new AbstractCommand(name, aliases) {
            @Override
            public Object execute(CommandSession session, String[] args) throws Exception {
                CommandResult result = commands
                        .execute(null, null, commandLine(name, args), line -> {
                            session.out().println(line);
                            session.out().flush();
                        })
                        .toCompletableFuture()
                        .join();

                if (!result.succeeded()) throw new FailedCommand(result.exitCode());
                return null;
            }
        };
    }

    /** Preserve argument boundaries when handing parsed Shell words to CommandMap. */
    static String commandLine(String name, String[] args) {
        StringBuilder line = new StringBuilder(name);
        for (String arg : args) {
            line.append(' ').append('"')
                    .append(arg.replace("\\", "\\\\").replace("\"", "\\\""))
                    .append('"');
        }
        return line.toString();
    }

    private static final class FailedCommand extends Exception {
        FailedCommand(int exitCode) {
            super("Command failed with exit code " + exitCode);
        }
    }
}
