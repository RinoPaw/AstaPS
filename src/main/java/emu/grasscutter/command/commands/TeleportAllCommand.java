package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import picocli.CommandLine;

@Command(
        label = "teleportAll",
        aliases = {"tpall"},
        permission = "player.tpall",
        permissionTargeted = "player.tpall.others")
public final class TeleportAllCommand implements PicocliCommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "teleportAll")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!targetPlayer.getWorld().isMultiplayer()) {
                CommandHandler.sendMessage(sender, translate(sender, "commands.teleportAll.error"));
                return;
            }

            for (Player player : targetPlayer.getWorld().getPlayers()) {
                if (player.equals(targetPlayer)) continue;
                player.getWorld()
                        .transferPlayerToScene(
                                player,
                                targetPlayer.getSceneId(),
                                TeleportType.COMMAND,
                                targetPlayer.getPosition());
            }

            CommandHandler.sendMessage(sender, translate(sender, "commands.teleportAll.success"));
        }
    }
}
