package emu.grasscutter.command.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "coop",
        permission = "server.coop",
        permissionTargeted = "server.coop.others")
public final class CoopCommand implements PicocliCommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "coop")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[hostUid]")
        private Integer hostUid;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            Player host;
            if (hostUid == null) {
                if (sender == null) {
                    CommandHandler.sendMessage(null, "A host UID is required from the console.");
                    return;
                }
                host = sender;
            } else {
                host = Grasscutter.getGameServer().getPlayerByUid(hostUid);
                if (host == null) {
                    CommandHandler.sendTranslatedMessage(sender, "commands.execution.player_offline_error");
                    return;
                }
            }

            if (targetPlayer.isInMultiplayer()) {
                targetPlayer.getServer().getMultiplayerSystem().leaveCoop(targetPlayer);
            }
            host.getServer().getMultiplayerSystem().applyEnterMp(targetPlayer, host.getUid());
            targetPlayer
                    .getServer()
                    .getMultiplayerSystem()
                    .applyEnterMpReply(host, targetPlayer.getUid(), true);
            CommandHandler.sendTranslatedMessage(
                    sender, "commands.coop.success", targetPlayer.getNickname(), host.getNickname());
        }
    }
}
