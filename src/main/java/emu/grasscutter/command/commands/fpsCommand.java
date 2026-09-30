package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketWindy;
import picocli.CommandLine;

@Command(label = "fps", permission = "player.windy", permissionTargeted = "player.windy.others")
public final class fpsCommand implements PicocliCommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(targetPlayer));
    }

    @CommandLine.Command(name = "fps")
    private static final class Args implements Runnable {
        private final Player targetPlayer;

        private Args(Player targetPlayer) {
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            targetPlayer.sendPacket(new PacketWindy("fps"));
        }
    }
}
