package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;

@Command(
        label = "unban",
        permission = "server.ban",
        targetRequirement = Command.TargetRequirement.PLAYER)
public final class UnBanCommand implements PicocliCommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "unban")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (unban(targetPlayer)) {
                CommandHandler.sendTranslatedMessage(sender, "commands.unban.success");
            } else {
                CommandHandler.sendTranslatedMessage(sender, "commands.unban.failure");
            }
        }

        private static boolean unban(Player targetPlayer) {
            Account account = targetPlayer.getAccount();
            if (account == null) return false;

            account.setBanReason(null);
            account.setBanEndTime(0);
            account.setBanStartTime(0);
            account.setBanned(false);
            account.save();
            return true;
        }
    }
}
