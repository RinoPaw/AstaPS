package emu.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class ServerLauncherTest {
    @Test
    void versionAndHelpExitWithoutRunningTheServer() {
        for (String arg : new String[] {"-V", "--version", "-h", "--help"}) {
            var output = new StringWriter();
            var command = new CommandLine(new ServerLauncher());
            command.setOut(new PrintWriter(output, true));

            assertEquals(0, command.execute(arg));
            assertFalse(output.toString().isBlank());
        }
    }

    @Test
    void conflictingModesAndUnknownOptionsFailDuringParsing() {
        for (String[] args : new String[][] {
                {"--game", "--dispatch"}, {"--unknown"}, {"--debug=invalid"}
        }) {
            var command = new CommandLine(new ServerLauncher());
            command.setErr(new PrintWriter(new StringWriter()));
            assertEquals(CommandLine.ExitCode.USAGE, command.execute(args));
        }
    }

    @Test
    void validOptionsAreParsedBeforeAnyServerInitialization() {
        var launcher = new ServerLauncher();
        var command = new CommandLine(launcher).setCaseInsensitiveEnumValuesAllowed(true);
        command.parseArgs("--dispatch", "--no-console", "--lang=zh-CN", "--debug=all");
        assertTrue(launcher.mode.dispatch);
        assertFalse(launcher.mode.game);
        assertTrue(launcher.noConsole);
        assertEquals("zh-CN", launcher.language);
        assertEquals(ServerLauncher.Debug.ALL, launcher.debug);

        var testLauncher = new ServerLauncher();
        new CommandLine(testLauncher).parseArgs("--test", "--dump=commands,en-US");
        assertTrue(testLauncher.test);
        assertEquals("commands,en-US", testLauncher.dump);
    }

    @Test
    void invalidDumpFailsWithoutStartingServer() {
        var command = new CommandLine(new ServerLauncher());
        command.setErr(new PrintWriter(new StringWriter()));
        assertEquals(CommandLine.ExitCode.USAGE, command.execute("--dump=invalid,en"));
    }

    @Test
    void incompatibleDumpOptionsFailWithoutStartingServer() {
        var command = new CommandLine(new ServerLauncher());
        command.setErr(new PrintWriter(new StringWriter()));
        assertEquals(
                CommandLine.ExitCode.USAGE,
                command.execute("--dump=commands,en-US", "--dump-packet-ids"));
    }

    @Test
    void debugWithoutValueSelectsStandardMode() {
        var launcher = new ServerLauncher();
        new CommandLine(launcher).parseArgs("--debug");
        assertEquals(ServerLauncher.Debug.STANDARD, launcher.debug);
    }
}