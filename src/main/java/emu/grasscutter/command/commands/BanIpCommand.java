package emu.grasscutter.command.commands;

import static emu.grasscutter.config.Configuration.HTTP_ENCRYPTION;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.game.BannedIp;
import emu.grasscutter.game.player.Player;
import java.util.Objects;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "banip",
        permission = "server.banip",
        targetRequirement = Command.TargetRequirement.NONE)
public final class BanIpCommand implements PicocliCommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender));
    }

    @CommandLine.Command(name = "banip")
    private static final class Args implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<key>")
        private String key;

        @Parameters(index = "1", paramLabel = "<ip>")
        private String ip;

        @Parameters(index = "2..*", arity = "0..*", paramLabel = "[reason]")
        private String[] reasonWords = new String[0];

        private Args(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            if (!Objects.equals(key, HTTP_ENCRYPTION.keystorePassword)) {
                CommandHandler.sendMessage(sender, "Wrong key.");
                return;
            }

            String reason = reasonWords.length == 0 ? "No reason given" : String.join(" ", reasonWords);
            new BannedIp(ip, reason).save();
            CommandHandler.sendMessage(sender, "Banned IP " + ip + ". Reason: " + reason);
        }
    }
}
