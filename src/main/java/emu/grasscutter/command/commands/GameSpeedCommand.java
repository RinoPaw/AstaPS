package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketWindy;
import java.util.Set;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "GameSpeed",
        aliases = {"speed"},
        permission = "player.windy",
        permissionTargeted = "player.windy.others")
public final class GameSpeedCommand implements PicocliCommandHandler {
    private static final Set<Double> SUPPORTED_SPEEDS =
            Set.of(0.1, 0.2, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0);

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "GameSpeed")
    private final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<speed>")
        private double speed;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!SUPPORTED_SPEEDS.contains(speed)) {
                GameSpeedCommand.this.sendUsageMessage(sender);
                return;
            }

            String text = Double.toString(speed);
            targetPlayer.sendPacket(new PacketWindy("GameSpeed/speed" + text));
            CommandHandler.sendMessage(sender, "GameSpeed changed to " + text + " successfully!");
        }
    }
}
