package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.avatar.AvatarExtraLevelHelper;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "extralevel",
        aliases = {"el", "levelbreak"},
        permission = "player.give",
        permissionTargeted = "player.give.others")
public final class ExtraLevelCommand implements CommandHandler {

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "extralevel")
    private final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[avatarId]")
        private Integer avatarId;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (targetPlayer == null) {
                CommandOutput.sendMessage(sender, "No target player.");
                return;
            }

            Avatar avatar;
            if (avatarId != null) {
                avatar = targetPlayer.getAvatars().getAvatarById(avatarId);
                if (avatar == null) {
                    CommandOutput.sendMessage(sender, "Avatar not found: " + avatarId);
                    return;
                }
            } else {
                EntityAvatar entity = targetPlayer.getTeamManager().getCurrentAvatarEntity();
                avatar = entity == null ? null : entity.getAvatar();
            }

            if (avatar == null) {
                CommandOutput.sendMessage(sender, "No current avatar.");
                return;
            }

            int oldLevel = avatar.getLevel();
            if (AvatarExtraLevelHelper.upgradeAvatar(targetPlayer, avatar)) {
                CommandOutput.sendMessage(
                        sender,
                        "Extra level OK: avatar "
                                + avatar.getAvatarId()
                                + " "
                                + oldLevel
                                + " -> "
                                + avatar.getLevel()
                                + " (cost 104300)");
            } else {
                CommandOutput.sendMessage(
                        sender,
                        "Extra level failed: need promote=6 and level 90 or 95, plus enough 104300. Now level="
                                + avatar.getLevel()
                                + " promote="
                                + avatar.getPromoteLevel());
            }
        }
    }
}
