package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketAvatarFetterDataNotify;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "setFetterLevel",
        aliases = {"setfetterlvl", "setfriendship"},
        permission = "player.setfetterlevel",
        permissionTargeted = "player.setfetterlevel.others")
public final class SetFetterLevelCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "setFetterLevel")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<level>")
        private int level;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (level < 0 || level > 10) {
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.setFetterLevel.range_error"));
                return;
            }

            Avatar avatar = targetPlayer.getTeamManager().getCurrentAvatarEntity().getAvatar();
            avatar.setFetterLevel(level);
            if (level != 10) {
                avatar.setFetterExp(GameData.getAvatarFetterLevelDataMap().get(level).getExp());
            }
            avatar.save();
            targetPlayer.sendPacket(new PacketAvatarFetterDataNotify(avatar));
            CommandOutput.sendMessage(
                    sender, translate(sender, "commands.setFetterLevel.success", level));
        }
    }
}
