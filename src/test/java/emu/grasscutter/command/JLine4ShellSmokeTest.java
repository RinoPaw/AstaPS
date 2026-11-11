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
 * Proves the native JLine 4 Shell semantics without executing a Grasscutter command or changing
 * player state. No production console path is switched until command completion has a real result.
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

}
