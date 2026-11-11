package emu.grasscutter.command;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.jline.shell.CommandSession;
import org.jline.shell.Shell;
import org.jline.shell.impl.AbstractCommand;
import org.jline.shell.impl.SimpleCommandGroup;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

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
            assertNotNull(shell.reader().getCompleter());

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
}
