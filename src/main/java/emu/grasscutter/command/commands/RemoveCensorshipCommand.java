package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketWindy;
import picocli.CommandLine;

@Command(
        label = "RemoveCensorship",
        aliases = {"cancer", "rc"},
        permission = "player.windy",
        permissionTargeted = "player.windy.others")
public final class RemoveCensorshipCommand implements PicocliCommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "RemoveCensorship")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            targetPlayer.sendPacket(new PacketWindy("RemoveCensorship"));
            CommandHandler.sendMessage(sender, "Censorship removed successfully.");
        }
    }
}
