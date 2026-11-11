package emu.grasscutter.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jline.reader.SyntaxError;
import org.jline.shell.CommandGroup;
import org.jline.shell.CommandSession;
import org.jline.shell.Pipeline;
import org.jline.shell.Shell;
import org.jline.shell.UnknownCommandException;
import org.jline.shell.impl.AbstractCommand;
import org.jline.shell.impl.DefaultCommandDispatcher;
import org.jline.shell.impl.PipelineParser;
import org.jline.terminal.Terminal;

/** Adapts the game's command registry and results to the native JLine 4 shell. */
public final class ConsoleShell {
    private ConsoleShell() {}

    public static Shell create(CommandMap commands, Terminal terminal) throws IOException {
        var dispatcher = new DefaultCommandDispatcher(terminal, null, pipelineParser(), null) {
            @Override
            public org.jline.shell.Command findCommand(String name) {
                var command = super.findCommand(name);
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
                for (Map.Entry<String, emu.grasscutter.command.Command> entry :
                        commands.getAnnotations().entrySet()) {
                    String[] aliases = java.util.Arrays.stream(entry.getValue().aliases())
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
                .onReaderReady((reader, d) -> commands.installConsoleCompleter(reader))
                .build();
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
