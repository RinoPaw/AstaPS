package emu.grasscutter.command;

import static org.junit.jupiter.api.Assertions.*;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.game.player.Player;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class ServerShellInteractiveTest {
    @Command(
            label = "echo",
            aliases = {"e"},
            inlineTarget = false,
            targetRequirement = Command.TargetRequirement.NONE,
            dispatchCompatible = true)
    private static final class EchoCommand implements CommandHandler {
        final List<List<String>> seen = new ArrayList<>();

        @Override
        public CommandLine createCommandLine(Player sender, Player target) {
            return new CommandLine(new Echo(seen));
        }

        @CommandLine.Command(name = "echo")
        private static final class Echo implements Runnable {
            final List<List<String>> seen;
            @CommandLine.Parameters(arity = "0..*")
            List<String> args = List.of();
            Echo(List<List<String>> seen) { this.seen = seen; }

            @Override
            public void run() {
                seen.add(List.copyOf(args));
                CommandOutput.sendMessage(null, String.join(" / ", args));
            }
        }
    }

    @Test
    void readerAndDispatcherPreserveQuotedArgumentsAndAliases() throws Exception {
        var original = Grasscutter.getPermissionHandler();
        Grasscutter.setPermissionHandler(new DefaultPermissionHandler());
        try {
            var map = new CommandMap(false, ServerRunMode.DISPATCH_ONLY);
            var echo = new EchoCommand();
            map.registerCommand("echo", echo);
            var out = new ByteArrayOutputStream();
            // Keep input open until readLine finishes; finite streams can race with EOF on Linux.
            try (var in = new PipedInputStream();
                    var feeder = new PipedOutputStream(in);
                    var terminal = TerminalBuilder.builder().system(false).dumb(true)
                            .streams(in, out).build();
                    var shell = ServerShellFactory.create(map, terminal)) {
                feeder.write("E  first  \"two words\"\n".getBytes(StandardCharsets.UTF_8));
                feeder.flush();
                shell.dispatcher().initialize(null);
                shell.dispatcher().execute(shell.reader().readLine("asta> "));
                shell.dispatcher().cleanUp();
            }
            assertEquals(List.of(List.of("first", "two words")), echo.seen);
            assertTrue(out.toString(StandardCharsets.UTF_8).contains("first / two words"));
        } finally {
            Grasscutter.setPermissionHandler(original);
        }
    }

    @Test
    void detachedInputRaisesEndOfFileInsteadOfSpinning() throws Exception {
        var map = new CommandMap(false, ServerRunMode.DISPATCH_ONLY);
        var in = new ByteArrayInputStream(new byte[0]);
        var out = new ByteArrayOutputStream();
        try (var terminal = TerminalBuilder.builder().system(false).dumb(true)
                .streams(in, out).build();
                var shell = ServerShellFactory.create(map, terminal)) {
            assertThrows(org.jline.reader.EndOfFileException.class,
                    () -> shell.reader().readLine("asta> "));
        }
    }

    @Test
    void tabCompletionDoesNotInsertDuplicateWhitespace() throws Exception {
        var map = new CommandMap(false, ServerRunMode.DISPATCH_ONLY);
        map.registerCommand("echo", new EchoCommand());
        var out = new ByteArrayOutputStream();
        try (var in = new PipedInputStream();
                var feeder = new PipedOutputStream(in);
                var terminal = TerminalBuilder.builder().system(false).dumb(true)
                        .streams(in, out).build();
                var shell = ServerShellFactory.create(map, terminal)) {
            feeder.write("ec\tvalue\n".getBytes(StandardCharsets.UTF_8));
            feeder.flush();
            assertEquals("echo value", shell.reader().readLine("asta> "));
        }
    }
}
