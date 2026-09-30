package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "setConst",
        aliases = {"setconstellation"},
        permission = "player.setconstellation",
        permissionTargeted = "player.setconstellation.others")
public final class SetConstCommand implements PicocliCommandHandler {

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "setConst")
    private final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<constellationLevel>")
        private int level;

        @Parameters(index = "1", arity = "0..1", paramLabel = "[all]")
        private String scope;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (level < -1 || level > 6) {
                CommandHandler.sendTranslatedMessage(sender, "commands.setConst.range_error");
                return;
            }

            if (scope == null) {
                EntityAvatar entity = targetPlayer.getTeamManager().getCurrentAvatarEntity();
                if (entity == null) {
                    return;
                }
                Avatar avatar = entity.getAvatar();
                setConstellation(targetPlayer, avatar, level);
                CommandHandler.sendTranslatedMessage(
                        sender, "commands.setConst.success", avatar.getAvatarData().getName(), level);
                return;
            }

            if (!scope.equalsIgnoreCase("all")) {
                SetConstCommand.this.sendUsageMessage(sender);
                return;
            }

            setAllConstellation(targetPlayer, level);
            CommandHandler.sendTranslatedMessage(sender, "commands.setConst.successall", level);
        }
    }

    private void setConstellation(Player player, Avatar avatar, int constLevel) {
        int currentConstLevel = avatar.getCoreProudSkillLevel();
        avatar.forceConstellationLevel(constLevel);
        if (constLevel < currentConstLevel) {
            reloadScene(player);
        }
        avatar.recalcConstellations();
        avatar.recalcStats(true);
        avatar.save();
    }

    private void setAllConstellation(Player player, int constLevel) {
        player.getAvatars().forEach(
                avatar -> {
                    avatar.forceConstellationLevel(constLevel);
                    avatar.recalcConstellations();
                    avatar.recalcStats(true);
                    avatar.save();
                });
        reloadScene(player);
    }

    private void reloadScene(Player player) {
        World world = player.getWorld();
        Scene scene = player.getScene();
        Position pos = player.getPosition();
        world.transferPlayerToScene(player, 1, pos);
        world.transferPlayerToScene(player, scene.getId(), pos);
        scene.broadcastPacket(new PacketSceneEntityAppearNotify(player));
    }
}
