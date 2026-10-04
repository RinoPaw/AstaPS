package emu.grasscutter.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class CommandMapParsingTest {
    @Test
    public void parserPreservesQuotedAndNegativeArguments() throws Exception {
        assertEquals(
                List.of("give", "1001", "hello world", "-5"),
                CommandMap.parseCommandTokens("give 1001 \"hello world\" -5"));
    }

    @Test
    public void targetSelectorAcceptsPlainAndAtUid() {
        assertEquals("123", CommandMap.normalizeTargetSelector("123"));
        assertEquals("123", CommandMap.normalizeTargetSelector("@123"));
        assertEquals("Alice", CommandMap.normalizeTargetSelector("Alice"));
    }

    @Test
    public void inlineTargetConsumptionRespectsCommandPolicy() {
        var commandLocalArgs = new ArrayList<>(List.of("clone", "@123"));
        assertNull(CommandMap.takeInlineTargetSelector(commandLocalArgs, false));
        assertEquals(List.of("clone", "@123"), commandLocalArgs);

        var globalTargetArgs = new ArrayList<>(List.of("foo", "@123", "bar"));
        assertEquals("123", CommandMap.takeInlineTargetSelector(globalTargetArgs, true));
        assertEquals(List.of("foo", "bar"), globalTargetArgs);
    }

    @Test
    public void rootDisablesPicocliArgumentFiles() {
        assertFalse(CommandMap.createRootCommandLine().isExpandAtFiles());
    }

    @Test
    public void aliasesFollowRegistrationAndUnregistration() {
        var map = new CommandMap(false);
        var handler = new ProbeCommand();

        map.registerCommand("probe", handler);
        assertSame(handler, map.getHandler("probe"));
        assertSame(handler, map.getHandler("p"));

        map.unregisterCommand("probe");
        assertNull(map.getHandler("probe"));
        assertNull(map.getHandler("p"));
    }

    @Command(label = "probe", aliases = {"p"}, targetRequirement = Command.TargetRequirement.NONE)
    private static final class ProbeCommand implements CommandHandler {
        @Override
        public CommandLine createCommandLine(
                emu.grasscutter.game.player.Player sender,
                emu.grasscutter.game.player.Player targetPlayer) {
            return new CommandLine((Runnable) () -> {});
        }
    }
}
