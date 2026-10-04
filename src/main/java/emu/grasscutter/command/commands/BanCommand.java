package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.BannedIp;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.game.GameSession;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(label = "ban", targetRequirement = Command.TargetRequirement.NONE)
public final class BanCommand implements CommandHandler {
    private static final int DEFAULT_BAN_END = 2051190000;

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("player", new BanPlayer(sender, targetPlayer));
        commandLine.addSubcommand("ip", new BanIp(sender));
        return commandLine;
    }

    @CommandLine.Command(name = "ban")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            BanCommand.this.sendUsageMessage(sender);
        }
    }

    @CommandLine.Command(name = "player")
    private static final class BanPlayer implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[endTime]")
        private Integer endTime;

        @Parameters(index = "1..*", arity = "0..*", paramLabel = "[reason]")
        private String[] reasonWords = new String[0];

        private BanPlayer(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (targetPlayer == null) {
                CommandOutput.sendTranslatedMessage(sender, "commands.execution.need_target");
                return;
            }
            if (!hasPermission(sender, targetPlayer, "server.ban", "server.ban.others")) return;

            int until = endTime == null ? DEFAULT_BAN_END : endTime;
            String reason =
                    reasonWords.length == 0 ? "Reason not specified." : String.join(" ", reasonWords);

            Account account = targetPlayer.getAccount();
            if (account == null) {
                CommandOutput.sendTranslatedMessage(sender, "commands.ban.failure");
                return;
            }

            account.setBanReason(reason);
            account.setBanEndTime(until);
            account.setBanStartTime((int) (System.currentTimeMillis() / 1000));
            account.setBanned(true);
            account.save();

            GameSession session = targetPlayer.getSession();
            if (session != null) session.close();
            CommandOutput.sendTranslatedMessage(sender, "commands.ban.success");
        }
    }

    @CommandLine.Command(name = "ip")
    private static final class BanIp implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<ip>")
        private String ip;

        @Parameters(index = "1..*", arity = "0..*", paramLabel = "[reason]")
        private String[] reasonWords = new String[0];

        private BanIp(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            if (!hasPermission(sender, sender, "server.banip", "server.banip")) return;
            String reason = reasonWords.length == 0 ? "No reason given" : String.join(" ", reasonWords);
            new BannedIp(ip, reason).save();
            CommandOutput.sendMessage(sender, "Banned IP " + ip + ". Reason: " + reason);
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
