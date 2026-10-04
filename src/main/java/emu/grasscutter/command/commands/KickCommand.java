package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.game.player.Player;
import java.util.Objects;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "kick",
        aliases = {"restart"},
        permissionTargeted = "server.kick")
public final class KickCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "kick")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<key>")
        private String key;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!Objects.equals(key, Configuration.HTTP_ENCRYPTION.keystorePassword)) {
                CommandOutput.sendMessage(sender != null ? sender : targetPlayer, "Wrong key");
                return;
            }

            if (sender != null) {
                CommandOutput.sendTranslatedMessage(
                        sender,
                        "commands.kick.player_kick_player",
                        sender.getUid(),
                        sender.getAccount().getUsername(),
                        targetPlayer.getUid(),
                        targetPlayer.getAccount().getUsername());
            } else {
                CommandOutput.sendTranslatedMessage(
                        null,
                        "commands.kick.server_kick_player",
                        targetPlayer.getUid(),
                        targetPlayer.getAccount().getUsername());
            }
            targetPlayer.getSession().close();
        }
    }
}
