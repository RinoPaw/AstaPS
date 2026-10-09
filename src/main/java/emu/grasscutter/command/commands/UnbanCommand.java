package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(label = "unban", targetRequirement = Command.TargetRequirement.NONE)
public final class UnbanCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("player", new UnbanPlayer(sender, targetPlayer));
        commandLine.addSubcommand("ip", new UnbanIp(sender));
        return commandLine;
    }

    @CommandLine.Command(name = "unban")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            UnbanCommand.this.sendUsageMessage(sender);
        }
    }

    @CommandLine.Command(name = "player")
    private static final class UnbanPlayer implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private UnbanPlayer(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (targetPlayer == null) {
                CommandOutput.sendTranslatedMessage(sender, "commands.execution.need_target");
                return;
            }
            if (!hasPermission(sender, targetPlayer, "server.ban", "server.ban")) return;

            Account account = targetPlayer.getAccount();
            if (account == null) {
                CommandOutput.sendTranslatedMessage(sender, "commands.unban.failure");
                return;
            }

            account.setBanReason(null);
            account.setBanEndTime(0);
            account.setBanStartTime(0);
            account.setBanned(false);
            account.save();
            CommandOutput.sendTranslatedMessage(sender, "commands.unban.success");
        }
    }

    @CommandLine.Command(name = "ip")
    private static final class UnbanIp implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<ip>")
        private String ip;

        private UnbanIp(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            if (!hasPermission(sender, sender, "server.banip", "server.banip")) return;
            if (!DatabaseHelper.removeBannedIp(ip)) {
                CommandOutput.sendMessage(sender, "No ban recorded for " + ip + ".");
                return;
            }

            int unbanned = DatabaseHelper.unbanAccountsBannedByIp(ip);
            CommandOutput.sendMessage(
                    sender,
                    unbanned > 0
                            ? "Unbanned IP " + ip + ", along with " + unbanned + " account(s)."
                            : "Unbanned IP " + ip + ".");
        }
    }

    private static boolean hasPermission(
            Player sender, Player targetPlayer, String permission, String permissionTargeted) {
        if (sender == null) return true;
        var account = sender.getAccount();
        String required = targetPlayer != null && targetPlayer != sender ? permissionTargeted : permission;
        if (account != null && account.hasPermission(required)) return true;
        CommandOutput.sendTranslatedMessage(sender, "commands.generic.permission_error");
        return false;
    }
}
