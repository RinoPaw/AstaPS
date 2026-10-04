package emu.grasscutter.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
    public void threadedExecutionUsesProvidedExecutor() {
        var submitted = new AtomicBoolean();
        var ran = new AtomicBoolean();

        CommandMap.executeCommand(
                () -> ran.set(true),
                true,
                command -> {
                    submitted.set(true);
                    command.run();
                });

        assertTrue(submitted.get());
        assertTrue(ran.get());
    }

    @Test
    public void synchronousExecutionDoesNotUseExecutor() {
        var submitted = new AtomicBoolean();
        var ran = new AtomicBoolean();

        CommandMap.executeCommand(
                () -> ran.set(true), false, command -> submitted.set(true));

        assertFalse(submitted.get());
        assertTrue(ran.get());
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

    @Test
    public void registrationBuildsPicocliRoutingTree() {
        var map = new CommandMap(false);
        map.registerCommand("probe", new ProbeCommand());

        var root = map.getCommandLine();
        var probe = root.getSubcommands().get("probe");
        assertTrue(root.getSubcommands().containsKey("p"));
        assertSame(probe, root.getSubcommands().get("p"));
        assertEquals("probe", probe.getCommandName());
        assertFalse(probe.isExpandAtFiles());

        map.unregisterCommand("probe");
        assertFalse(map.getCommandLine().getSubcommands().containsKey("probe"));
        assertFalse(map.getCommandLine().getSubcommands().containsKey("p"));
    }

    @Test
    public void runtimeRegistrationAndUnregistrationUpdateTreeIncrementally() {
        var map = new CommandMap(false);
        var root = map.getCommandLine();
        var first = new BatchFirstCommand();
        var second = new BatchSecondCommand();

        map.registerCommand("batch-first", first);
        assertSame(root, map.getCommandLine());
        assertEquals(1, first.completionBuilds.get());

        map.registerCommand("batch-second", second);
        assertSame(root, map.getCommandLine());
        assertEquals(1, first.completionBuilds.get());
        assertEquals(1, second.completionBuilds.get());
        assertTrue(root.getSubcommands().containsKey("batch-first"));
        assertTrue(root.getSubcommands().containsKey("batch-second"));

        map.unregisterCommand("batch-second");
        assertSame(root, map.getCommandLine());
        assertEquals(1, first.completionBuilds.get());
        assertEquals(1, second.completionBuilds.get());
        assertTrue(root.getSubcommands().containsKey("batch-first"));
        assertFalse(root.getSubcommands().containsKey("batch-second"));
    }

    @Test
    public void batchRegistrationBuildsEachCompletionModelOnce() {
        var map = new CommandMap(false);
        var first = new BatchFirstCommand();
        var second = new BatchSecondCommand();

        map.registerCommands(List.of(first, second));

        assertEquals(1, first.completionBuilds.get());
        assertEquals(1, second.completionBuilds.get());
        assertSame(first, map.getHandler("batch-first"));
        assertSame(second, map.getHandler("batch-second"));
    }

    @Test
    public void batchRegistrationRollsBackOnCollision() {
        var map = new CommandMap(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> map.registerCommands(List.of(new ProbeCommand(), new AliasCollisionCommand())));

        assertNull(map.getHandler("probe"));
        assertNull(map.getHandler("other"));
        assertFalse(map.getCommandLine().getSubcommands().containsKey("probe"));
        assertFalse(map.getCommandLine().getSubcommands().containsKey("other"));
    }

    @Test
    public void registrationRejectsUnannotatedHandlers() {
        var map = new CommandMap(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> map.registerCommand("invalid", new UnannotatedCommand()));
        assertNull(map.getHandler("invalid"));
        assertFalse(map.getCommandLine().getSubcommands().containsKey("invalid"));
    }

    @Test
    public void registrationRejectsExistingLabelWithoutChangingRouting() {
        var map = new CommandMap(false);
        var original = new ProbeCommand();
        map.registerCommand("probe", original);

        assertThrows(
                IllegalArgumentException.class,
                () -> map.registerCommand("probe", new ReplacementProbeCommand()));

        assertSame(original, map.getHandler("probe"));
        assertSame(original, map.getHandler("p"));
        assertTrue(map.getCommandLine().getSubcommands().containsKey("probe"));
    }

    @Test
    public void registrationRejectsAliasCollisionWithExistingCommand() {
        var map = new CommandMap(false);
        var original = new ProbeCommand();
        map.registerCommand("probe", original);

        assertThrows(
                IllegalArgumentException.class,
                () -> map.registerCommand("other", new AliasCollisionCommand()));

        assertSame(original, map.getHandler("probe"));
        assertNull(map.getHandler("other"));
        assertFalse(map.getCommandLine().getSubcommands().containsKey("other"));
    }

    @Test
    public void registrationRejectsDuplicateAliasesIgnoringCase() {
        var map = new CommandMap(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> map.registerCommand("duplicate", new DuplicateAliasCommand()));
        assertNull(map.getHandler("duplicate"));
        assertNull(map.getHandler("d"));
    }

    @Test
    public void handlerMapIsASnapshot() {
        var map = new CommandMap(false);
        var handler = new ProbeCommand();
        map.registerCommand("probe", handler);

        map.getHandlers().clear();

        assertSame(handler, map.getHandler("probe"));
        assertTrue(map.getCommandLine().getSubcommands().containsKey("probe"));
    }

    @Command(label = "probe", aliases = {"p"}, targetRequirement = Command.TargetRequirement.NONE)
    private static final class ProbeCommand implements CommandHandler {
        @Override
        public CommandLine createCommandLine(
                emu.grasscutter.game.player.Player sender,
                emu.grasscutter.game.player.Player targetPlayer) {
            return new CommandLine(CommandLine.Model.CommandSpec.create());
        }
    }

    @Command(label = "probe", targetRequirement = Command.TargetRequirement.NONE)
    private static final class ReplacementProbeCommand implements CommandHandler {
        @Override
        public CommandLine createCommandLine(
                emu.grasscutter.game.player.Player sender,
                emu.grasscutter.game.player.Player targetPlayer) {
            return new CommandLine(CommandLine.Model.CommandSpec.create());
        }
    }

    @Command(
            label = "other",
            aliases = {"probe"},
            targetRequirement = Command.TargetRequirement.NONE)
    private static final class AliasCollisionCommand implements CommandHandler {
        @Override
        public CommandLine createCommandLine(
                emu.grasscutter.game.player.Player sender,
                emu.grasscutter.game.player.Player targetPlayer) {
            return new CommandLine(CommandLine.Model.CommandSpec.create());
        }
    }

    @Command(
            label = "duplicate",
            aliases = {"d", "D"},
            targetRequirement = Command.TargetRequirement.NONE)
    private static final class DuplicateAliasCommand implements CommandHandler {
        @Override
        public CommandLine createCommandLine(
                emu.grasscutter.game.player.Player sender,
                emu.grasscutter.game.player.Player targetPlayer) {
            return new CommandLine(CommandLine.Model.CommandSpec.create());
        }
    }

    @Command(label = "batch-first", targetRequirement = Command.TargetRequirement.NONE)
    private static final class BatchFirstCommand implements CommandHandler {
        private final AtomicInteger completionBuilds = new AtomicInteger();

        @Override
        public CommandLine createCommandLine(
                emu.grasscutter.game.player.Player sender,
                emu.grasscutter.game.player.Player targetPlayer) {
            if (sender == null && targetPlayer == null) this.completionBuilds.incrementAndGet();
            return new CommandLine(CommandLine.Model.CommandSpec.create());
        }
    }

    @Command(label = "batch-second", targetRequirement = Command.TargetRequirement.NONE)
    private static final class BatchSecondCommand implements CommandHandler {
        private final AtomicInteger completionBuilds = new AtomicInteger();

        @Override
        public CommandLine createCommandLine(
                emu.grasscutter.game.player.Player sender,
                emu.grasscutter.game.player.Player targetPlayer) {
            if (sender == null && targetPlayer == null) this.completionBuilds.incrementAndGet();
            return new CommandLine(CommandLine.Model.CommandSpec.create());
        }
    }

    private static final class UnannotatedCommand implements CommandHandler {
        @Override
        public CommandLine createCommandLine(
                emu.grasscutter.game.player.Player sender,
                emu.grasscutter.game.player.Player targetPlayer) {
            return new CommandLine(CommandLine.Model.CommandSpec.create());
        }
    }
}
