package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketWindy;
import picocli.CommandLine;

@Command(
        label = "HideUI",
        aliases = {"hui"},
        permission = "player.windy",
        permissionTargeted = "player.windy.others")
public final class HideUICommand implements PicocliCommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "HideUI")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            targetPlayer.sendPacket(new PacketWindy("HideUI"));
            CommandHandler.sendMessage(sender, "UI hidden successfully.");
        }
    }
}
