package emu.grasscutter.command;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.command.commands.HelpCommand;
import emu.grasscutter.command.commands.InfoCommand;
import emu.grasscutter.command.commands.SayCommand;
import emu.grasscutter.command.commands.StopCommand;
import java.util.List;
import java.util.Set;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

public final class DispatchCommandAvailabilityTest {
    @Test
    void dispatchRegistersOnlyCompatibleCommands() {
        var map = new CommandMap(false, ServerRunMode.DISPATCH_ONLY);
        map.registerCommands(List.of(
                new HelpCommand(), new InfoCommand(), new StopCommand(), new SayCommand()));

        assertEquals(Set.of("help", "info", "stop"), map.getHandlers().keySet());
        assertTrue(map.getCommandLine().getSubcommands().containsKey("help"));
        assertTrue(map.getCommandLine().getSubcommands().containsKey("shutdown"));
        assertFalse(map.getCommandLine().getSubcommands().containsKey("say"));
        assertNull(map.getHandler("say"));
        assertNotNull(map.getHandler("shutdown"));
    }

    @Test
    void dispatchAlsoFiltersSingleRegistrations() {
        var map = new CommandMap(false, ServerRunMode.DISPATCH_ONLY);
        map.registerCommand("say", new SayCommand());
        map.registerCommand("help", new HelpCommand());
        assertNull(map.getHandler("say"));
        assertNotNull(map.getHandler("help"));
    }

    @Test
    void dispatchShellDoesNotExposePlayerTargeting() throws Exception {
        var map = new CommandMap(false, ServerRunMode.DISPATCH_ONLY);
        map.registerCommand("help", new HelpCommand());
        try (var terminal = TerminalBuilder.builder().dumb(true).build();
                var shell = ConsoleShell.create(map, terminal)) {
            var names = shell.dispatcher().groups().stream()
                    .flatMap(group -> group.commands().stream())
                    .map(org.jline.shell.Command::name)
                    .toList();
            assertFalse(names.contains("target"));
            assertTrue(names.contains("help"));
        }
    }

    @Test
    void gameModeRetainsCommands() {
        var map = new CommandMap(false, ServerRunMode.GAME_ONLY);
        map.registerCommands(List.of(new HelpCommand(), new SayCommand()));
        assertEquals(Set.of("help", "say"), map.getHandlers().keySet());
    }
}
