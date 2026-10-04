package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.achievement.AchievementData;
import emu.grasscutter.game.achievement.AchievementControlReturns;
import emu.grasscutter.game.achievement.Achievements;
import emu.grasscutter.game.player.Player;
import java.util.concurrent.atomic.AtomicInteger;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "achievement",
        aliases = {"am"},
        permission = "player.achievement",
        permissionTargeted = "player.achievement.others",
        targetRequirement = Command.TargetRequirement.PLAYER,
        threading = true)
public final class AchievementCommand implements CommandHandler {

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("grant", new Grant(sender, targetPlayer));
        commandLine.addSubcommand("revoke", new Revoke(sender, targetPlayer));
        commandLine.addSubcommand("progress", new Progress(sender, targetPlayer));
        commandLine.addSubcommand("grantall", new GrantAll(sender, targetPlayer));
        commandLine.addSubcommand("revokeall", new RevokeAll(sender, targetPlayer));
        return commandLine;
    }

    @CommandLine.Command(name = "achievement")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            AchievementCommand.this.sendUsageMessage(sender);
        }
    }

    private abstract static class AchievementCommandBase implements Runnable {
        protected final Player sender;
        protected final Player targetPlayer;

        private AchievementCommandBase(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        protected Achievements achievements() {
            return Achievements.getByPlayer(targetPlayer);
        }
    }

    private static final class Grant extends AchievementCommandBase {
        @Parameters(index = "0", paramLabel = "<achievementId>")
        private int achievementId;

        private Grant(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            var result = achievements().grant(achievementId);
            switch (result.getRet()) {
                case SUCCESS -> sendSuccessMessage(sender, "grant", targetPlayer.getNickname());
                case ACHIEVEMENT_NOT_FOUND -> CommandOutput.sendTranslatedMessage(
                        sender, result.getRet().getKey());
                case ALREADY_ACHIEVED -> CommandOutput.sendTranslatedMessage(
                        sender, result.getRet().getKey(), targetPlayer.getNickname());
            }
        }
    }

    private static final class Revoke extends AchievementCommandBase {
        @Parameters(index = "0", paramLabel = "<achievementId>")
        private int achievementId;

        private Revoke(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            var result = achievements().revoke(achievementId);
            switch (result.getRet()) {
                case SUCCESS -> sendSuccessMessage(sender, "revoke", targetPlayer.getNickname());
                case ACHIEVEMENT_NOT_FOUND -> CommandOutput.sendTranslatedMessage(
                        sender, result.getRet().getKey());
                case NOT_YET_ACHIEVED -> CommandOutput.sendTranslatedMessage(
                        sender, result.getRet().getKey(), targetPlayer.getNickname());
            }
        }
    }

    private static final class Progress extends AchievementCommandBase {
        @Parameters(index = "0", paramLabel = "<achievementId>")
        private int achievementId;

        @Parameters(index = "1", paramLabel = "<progress>")
        private int progress;

        private Progress(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            var result = achievements().progress(achievementId, progress);
            switch (result.getRet()) {
                case SUCCESS -> sendSuccessMessage(
                        sender, "progress", targetPlayer.getNickname(), achievementId, progress);
                case ACHIEVEMENT_NOT_FOUND -> CommandOutput.sendTranslatedMessage(
                        sender, result.getRet().getKey());
            }
        }
    }

    @CommandLine.Command(name = "grantall")
    private static final class GrantAll extends AchievementCommandBase {
        private GrantAll(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            var counter = new AtomicInteger();
            GameData.getAchievementDataMap().values().stream()
                    .filter(AchievementData::isUsed)
                    .filter(AchievementData::isParent)
                    .forEach(
                            data -> {
                                var result = achievements().grant(data.getId());
                                if (result.getRet() == AchievementControlReturns.Return.SUCCESS) {
                                    counter.addAndGet(result.getChangedAchievementStatusNum());
                                }
                            });
            sendSuccessMessage(sender, "grantall", counter.get(), targetPlayer.getNickname());
        }
    }

    @CommandLine.Command(name = "revokeall")
    private static final class RevokeAll extends AchievementCommandBase {
        private RevokeAll(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            var counter = new AtomicInteger();
            GameData.getAchievementDataMap().values().stream()
                    .filter(AchievementData::isUsed)
                    .filter(AchievementData::isParent)
                    .forEach(
                            data -> {
                                var result = achievements().revoke(data.getId());
                                if (result.getRet() == AchievementControlReturns.Return.SUCCESS) {
                                    counter.addAndGet(result.getChangedAchievementStatusNum());
                                }
                            });
            sendSuccessMessage(sender, "revokeall", counter.get(), targetPlayer.getNickname());
        }
    }

    private static void sendSuccessMessage(Player sender, String command, Object... args) {
        CommandOutput.sendTranslatedMessage(
                sender, AchievementControlReturns.Return.SUCCESS.getKey() + command, args);
    }
}
