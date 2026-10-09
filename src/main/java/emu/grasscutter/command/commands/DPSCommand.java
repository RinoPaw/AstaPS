package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.dps.DPSMeter;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

/** Controls the in-server DPS test. */
@Command(label = "dps", targetRequirement = Command.TargetRequirement.ONLINE)
public final class DPSCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var root = new CommandLine(new Root(sender));
        root.addSubcommand("start", new Start(targetPlayer));
        root.addSubcommand("stop", new Stop(targetPlayer));
        return root;
    }

    @CommandLine.Command(name = "dps")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            DPSCommand.this.sendUsageMessage(sender);
        }
    }

    @CommandLine.Command(name = "start")
    private static final class Start implements Runnable {
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[seconds]")
        private Integer seconds;

        @Parameters(index = "1", arity = "0..1", paramLabel = "[targetCount]")
        private Integer targetCount;

        private Start(Player targetPlayer) {
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            int duration = seconds == null ? DPSMeter.DEFAULT_SECONDS : seconds;
            int count = targetCount == null ? 1 : targetCount;
            if (duration <= 0 || count <= 0) {
                CommandOutput.sendMessage(targetPlayer, "seconds and targetCount must be positive.");
                return;
            }
            DPSMeter.start(targetPlayer, duration, count);
        }
    }

    @CommandLine.Command(name = "stop")
    private static final class Stop implements Runnable {
        private final Player targetPlayer;

        private Stop(Player targetPlayer) {
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            DPSMeter.stop(targetPlayer);
        }
    }
}
