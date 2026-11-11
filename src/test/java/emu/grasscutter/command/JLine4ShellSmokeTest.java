package emu.grasscutter.command;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.jline.reader.Parser;
import org.jline.reader.impl.DefaultParser;
import org.jline.shell.CommandSession;
import org.jline.shell.Shell;
import org.jline.shell.impl.AbstractCommand;
import org.jline.shell.impl.SimpleCommandGroup;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * Proves JLine 4 Shell semantics without running game commands or changing player state.
 */
class JLine4ShellSmokeTest {
    @Test
    void shellCompletesAndChainsByRealFailure() throws Exception {
        var executions = new AtomicInteger();

        try (var terminal = TerminalBuilder.builder().dumb(true).build();
                var shell =
                        Shell.builder()
                                .terminal(terminal)
                                .groups(
                                        new SimpleCommandGroup(
                                                "probe",
                                                new AbstractCommand("ok") {
                                                    @Override
                                                    public Object execute(CommandSession session, String[] args) {
                                                        return null;
                                                    }
                                                },
                                                new AbstractCommand("fail") {
                                                    @Override
                                                    public Object execute(CommandSession session, String[] args) {
                                                        throw new IllegalStateException("expected failure");
                                                    }
                                                },
                                                new AbstractCommand("count") {
                                                    @Override
                                                    public Object execute(CommandSession session, String[] args) {
                                                        executions.incrementAndGet();
                                                        return null;
                                                    }
                                                }))
                                .build()) {
            var candidates = new ArrayList<org.jline.reader.Candidate>();
            shell.dispatcher()
                    .completer()
                    .complete(
                            shell.reader(),
                            new DefaultParser().parse("co", 2, Parser.ParseContext.COMPLETE),
                            candidates);
            assertTrue(candidates.stream().anyMatch(candidate -> candidate.value().equals("count")));

            shell.dispatcher().execute("fail && count");
            assertEquals(0, executions.get());

            shell.dispatcher().execute("fail || count");
            assertEquals(1, executions.get());

            shell.dispatcher().execute("ok && count");
            assertEquals(2, executions.get());

            shell.dispatcher().execute("ok || count");
            assertEquals(2, executions.get());
        }
    }

    @Test
    void returningIntegerDoesNotSetJLine4ExitStatus() throws Exception {
        var executions = new AtomicInteger();

        try (var terminal = TerminalBuilder.builder().dumb(true).build();
                var shell =
                        Shell.builder()
                                .terminal(terminal)
                                .groups(
                                        new SimpleCommandGroup(
                                                "probe",
                                                new AbstractCommand("returns-one") {
                                                    @Override
                                                    public Object execute(CommandSession session, String[] args) {
                                                        return 1;
                                                    }
                                                },
                                                new AbstractCommand("count") {
                                                    @Override
                                                    public Object execute(CommandSession session, String[] args) {
                                                        executions.incrementAndGet();
                                                        return null;
                                                    }
                                                }))
                                .build()) {
            // JLine 4.5.0 considers every normally returned Object a success.
            // A Picocli integer exit status therefore needs an explicit execution contract.
            shell.dispatcher().execute("returns-one && count");
            assertEquals(1, executions.get());
        }
    }
    @CommandLine.Command(name = "pure")
    static class PurePicocliCommand implements java.util.concurrent.Callable<Integer> {
        @CommandLine.Option(names = "--fail")
        boolean fail;

        @Override
        public Integer call() {
            return fail ? 7 : 0;
        }
    }

    @Test
    void picocliFailureMustBeSignaledToShell() throws Exception {
        var executions = new AtomicInteger();

        try (var terminal = TerminalBuilder.builder().dumb(true).build();
                var shell =
                        Shell.builder()
                                .terminal(terminal)
                                .groups(
                                        new SimpleCommandGroup(
                                                "probe",
                                                new AbstractCommand("pure") {
                                                    @Override
                                                    public Object execute(CommandSession session, String[] args) {
                                                        int code = new CommandLine(new PurePicocliCommand()).execute(args);
                                                        if (code != 0) {
                                                            throw new IllegalStateException("Picocli exit status: " + code);
                                                        }
                                                        return null;
                                                    }
                                                },
                                                new AbstractCommand("count") {
                                                    @Override
                                                    public Object execute(CommandSession session, String[] args) {
                                                        executions.incrementAndGet();
                                                        return null;
                                                    }
                                                }))
                                .build()) {
            shell.dispatcher().execute("pure --fail && count");
            assertEquals(0, executions.get());

            shell.dispatcher().execute("pure --fail || count");
            assertEquals(1, executions.get());

            shell.dispatcher().execute("pure && count");
            assertEquals(2, executions.get());
        }
    }

    @Test
    void consoleShellKeepsFileRedirectionDisabled() {
        var parser = ConsoleShell.pipelineParser();
        var conditional = parser.parse("help && help || help");
        assertEquals(org.jline.shell.Pipeline.Operator.AND, conditional.stages().get(0).operator());
        assertEquals(org.jline.shell.Pipeline.Operator.OR, conditional.stages().get(1).operator());

        var redirect = parser.parse("help > output.txt");
        assertEquals(1, redirect.stages().size());
        assertNull(redirect.stages().get(0).redirectTarget());

        var pipe = parser.parse("help | help");
        assertEquals(1, pipe.stages().size());
    }

    @Test
    void consoleShellDoesNotRegisterAliasesAsSeparateCommands() throws Exception {
        var commands = new CommandMap(false);
        commands.registerCommand("lookup", new emu.grasscutter.command.commands.LookupCommand());

        try (var terminal = TerminalBuilder.builder().dumb(true).build();
                var shell = ConsoleShell.create(commands, terminal)) {
            var group = shell.dispatcher().groups().stream()
                    .filter(entry -> entry.name().equals("astaps"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(
                    java.util.Set.of("lookup", "target"),
                    group.commands().stream()
                            .map(org.jline.shell.Command::name)
                            .collect(java.util.stream.Collectors.toSet()));
            assertEquals(2, group.commands().size());
            assertNotNull(shell.dispatcher().findCommand("lookup"));
            assertNotNull(shell.dispatcher().findCommand("find"));
            assertNotNull(shell.dispatcher().findCommand("search"));
        }
    }

    @Test
    void commandHighlighterDistinguishesKnownAliasesAndUnknownCommands() throws Exception {
        var commands = new CommandMap(false);
        commands.registerCommand("lookup", new emu.grasscutter.command.commands.LookupCommand());

        try (var terminal = TerminalBuilder.builder().dumb(true).build();
                var shell = ConsoleShell.create(commands, terminal)) {
            var highlighter = shell.reader().getHighlighter();
            String known = highlighter.highlight(shell.reader(), "lookup").toAnsi();
            String alias = highlighter.highlight(shell.reader(), "find").toAnsi();
            String unknown = highlighter.highlight(shell.reader(), "__unknown_probe__").toAnsi();
            String operator = highlighter
                    .highlight(shell.reader(), "lookup && __unknown_probe__")
                    .toAnsi();

            assertTrue(known.contains("\u001B[1m"), known);
            assertTrue(alias.contains("\u001B[1m"), alias);
            assertTrue(unknown.contains("\u001B[31m"), unknown);
            assertTrue(operator.contains("\u001B[36m"), operator);
        }
    }

    @Test
    void consoleCompletionObservesCommandsRegisteredAfterShellCreation() throws Exception {
        var commands = new CommandMap(false);
        try (var terminal = TerminalBuilder.builder().dumb(true).build();
                var shell = ConsoleShell.create(commands, terminal)) {
            commands.registerCommand("lookup", new emu.grasscutter.command.commands.LookupCommand());
            var candidates = new ArrayList<org.jline.reader.Candidate>();
            shell.dispatcher().completer().complete(
                    shell.reader(),
                    new DefaultParser().parse("loo", 3, Parser.ParseContext.COMPLETE),
                    candidates);
            assertTrue(candidates.stream().anyMatch(candidate -> candidate.value().equals("lookup")));
            assertNotNull(shell.dispatcher().findCommand("find"));
        }
    }

    @Test
    void consoleShellRegistersTheTargetPseudoCommand() throws Exception {
        try (var terminal = TerminalBuilder.builder().dumb(true).build();
                var shell = ConsoleShell.create(new CommandMap(false), terminal)) {
            assertNotNull(shell.dispatcher().findCommand("target"));
            assertNotNull(shell.dispatcher().findCommand("TARGET"));
            assertEquals("unknown-probe", shell.dispatcher().findCommand("unknown-probe").name());
            assertEquals("exit", shell.dispatcher().findCommand("exit").name());
            assertEquals("exit", shell.dispatcher().findCommand("EXIT").name());
        }
    }

}
