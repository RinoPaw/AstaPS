package emu.grasscutter.command.commands;

import static emu.grasscutter.config.Configuration.HTTP_ENCRYPTION;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import java.util.Objects;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "unbanip",
        permission = "server.banip",
        targetRequirement = Command.TargetRequirement.NONE)
public final class UnBanIpCommand implements PicocliCommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender));
    }

    @CommandLine.Command(name = "unbanip")
    private static final class Args implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<key>")
        private String key;

        @Parameters(index = "1", paramLabel = "<ip>")
        private String ip;

        private Args(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            if (!Objects.equals(key, HTTP_ENCRYPTION.keystorePassword)) {
                CommandHandler.sendMessage(sender, "Wrong key.");
                return;
            }

            if (!DatabaseHelper.removeBannedIp(ip)) {
                CommandHandler.sendMessage(sender, "No ban recorded for " + ip + ".");
                return;
            }

            var unbanned = DatabaseHelper.unbanAccountsBannedByIp(ip);
            CommandHandler.sendMessage(
                    sender,
                    unbanned > 0
                            ? "Unbanned IP " + ip + ", along with " + unbanned + " account(s)."
                            : "Unbanned IP " + ip + ".");
        }
    }
}
