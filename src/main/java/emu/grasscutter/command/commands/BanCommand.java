package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.game.GameSession;
import java.util.Objects;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "ban",
        permission = "server.ban",
        targetRequirement = Command.TargetRequirement.PLAYER)
public final class BanCommand implements PicocliCommandHandler {
    private static final int DEFAULT_BAN_END = 2051190000;

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "ban")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<key>")
        private String key;

        @Parameters(index = "1", arity = "0..1", paramLabel = "[endTime]")
        private Integer endTime;

        @Parameters(index = "2..*", arity = "0..*", paramLabel = "[reason]")
        private String[] reasonWords = new String[0];

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!Objects.equals(key, Configuration.HTTP_ENCRYPTION.keystorePassword)) {
                CommandHandler.sendMessage(sender != null ? sender : targetPlayer, "Wrong key");
                return;
            }

            int until = endTime == null ? DEFAULT_BAN_END : endTime;
            String reason =
                    reasonWords.length == 0 ? "Reason not specified." : String.join(" ", reasonWords);

            if (banAccount(targetPlayer, until, reason)) {
                CommandHandler.sendTranslatedMessage(sender, "commands.ban.success");
            } else {
                CommandHandler.sendTranslatedMessage(sender, "commands.ban.failure");
            }
        }

        private static boolean banAccount(Player targetPlayer, int endTime, String reason) {
            Account account = targetPlayer.getAccount();
            if (account == null) return false;

            account.setBanReason(reason);
            account.setBanEndTime(endTime);
            account.setBanStartTime((int) (System.currentTimeMillis() / 1000));
            account.setBanned(true);
            account.save();

            GameSession session = targetPlayer.getSession();
            if (session != null) session.close();
            return true;
        }
    }
}
