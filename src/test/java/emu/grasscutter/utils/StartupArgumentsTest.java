package emu.grasscutter.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class StartupArgumentsTest {
    @Test
    public void acceptsLegacyEqualsSyntaxAndOptionCase() {
        var options = new StartupArguments.Options();
        var commandLine = StartupArguments.createCommandLine(options);

        commandLine.parseArgs(
                "-DEBUG=all",
                "-LANG=en-US",
                "-GAME",
                "-NOCONSOLE",
                "-DUMP=items,en");

        assertEquals(StartupArguments.DebugMode.ALL, options.debugMode);
        assertEquals("en-US", options.language);
        assertNotNull(options.runMode);
        assertTrue(options.runMode.game);
        assertFalse(options.runMode.dispatch);
        assertTrue(options.noConsole);
        assertEquals(
                new StartupArguments.DumpRequest(StartupArguments.DumpContent.ITEMS, "en"),
                options.dumpRequest);
    }

    @Test
    public void debugWithoutValueUsesBasicMode() {
        var options = new StartupArguments.Options();

        StartupArguments.createCommandLine(options).parseArgs("-debug");

        assertEquals(StartupArguments.DebugMode.BASIC, options.debugMode);
    }

    @Test
    public void versionAliasIsAccepted() {
        var options = new StartupArguments.Options();

        StartupArguments.createCommandLine(options).parseArgs("-v");

        assertTrue(options.version);
    }

    @Test
    public void gameAndDispatchModesAreExclusive() {
        var options = new StartupArguments.Options();

        assertThrows(
                CommandLine.ParameterException.class,
                () -> StartupArguments.createCommandLine(options).parseArgs("-game", "-dispatch"));
    }

    @Test
    public void dumpRequiresContentAndLanguage() {
        var options = new StartupArguments.Options();

        assertThrows(
                CommandLine.ParameterException.class,
                () -> StartupArguments.createCommandLine(options).parseArgs("-dump=items"));
        assertThrows(
                CommandLine.ParameterException.class,
                () -> StartupArguments.createCommandLine(options).parseArgs("-dump=unknown,en"));
    }
}
