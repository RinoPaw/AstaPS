package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(label = "constellation")
public final class ConstellationCommand implements CommandHandler {
    private enum Scope {
        ALL
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        commandLine.addSubcommand("set", new Set(sender, targetPlayer));
        commandLine.addSubcommand("reset", new Reset(sender, targetPlayer));
        return commandLine;
    }

    @CommandLine.Command(name = "constellation")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            ConstellationCommand.this.sendUsageMessage(sender);
        }
    }

    @CommandLine.Command(name = "set")
    private static final class Set implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<0-6>")
        private int level;

        @Parameters(index = "1", arity = "0..1", paramLabel = "[all]")
        private Scope scope;

        private Set(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!hasPermission(
                    sender,
                    targetPlayer,
                    "player.setconstellation",
                    "player.setconstellation.others")) return;
            if (level < 0 || level > 6) {
                CommandOutput.sendTranslatedMessage(sender, "commands.setConst.range_error");
                return;
            }

            if (scope == Scope.ALL) {
                targetPlayer.getAvatars().forEach(avatar -> apply(targetPlayer, avatar, level, false));
                reloadScene(targetPlayer);
                CommandOutput.sendTranslatedMessage(sender, "commands.setConst.successall", level);
                return;
            }

            EntityAvatar entity = targetPlayer.getTeamManager().getCurrentAvatarEntity();
            if (entity == null) {
                CommandOutput.sendMessage(sender, "No active character.");
                return;
            }
            Avatar avatar = entity.getAvatar();
            apply(targetPlayer, avatar, level, true);
            CommandOutput.sendTranslatedMessage(
                    sender, "commands.setConst.success", avatar.getAvatarData().getName(), level);
        }
    }

    @CommandLine.Command(name = "reset")
    private static final class Reset implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[all]")
        private Scope scope;

        private Reset(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!hasPermission(
                    sender,
                    targetPlayer,
                    "player.resetconstellation",
                    "player.resetconstellation.others")) return;

            if (scope == Scope.ALL) {
                targetPlayer.getAvatars().forEach(avatar -> apply(targetPlayer, avatar, -1, false));
                reloadScene(targetPlayer);
                CommandOutput.sendTranslatedMessage(sender, "commands.resetConst.reset_all");
                return;
            }

            EntityAvatar entity = targetPlayer.getTeamManager().getCurrentAvatarEntity();
            if (entity == null) {
                CommandOutput.sendMessage(sender, "No active character.");
                return;
            }
            Avatar avatar = entity.getAvatar();
            apply(targetPlayer, avatar, -1, true);
            CommandOutput.sendTranslatedMessage(
                    sender, "commands.resetConst.success", avatar.getAvatarData().getName());
        }
    }

    private static void apply(Player player, Avatar avatar, int level, boolean reloadIfLowered) {
        int before = avatar.getCoreProudSkillLevel();
        avatar.forceConstellationLevel(level);
        avatar.recalcConstellations();
        avatar.recalcStats(true);
        avatar.save();
        if (reloadIfLowered && level < before) reloadScene(player);
    }

    private static void reloadScene(Player player) {
        World world = player.getWorld();
        Scene scene = player.getScene();
        if (world == null || scene == null) return;
        Position pos = new Position(player.getPosition());
        int sceneId = scene.getId();
        world.transferPlayerToScene(player, 1, pos);
        world.transferPlayerToScene(player, sceneId, pos);
        player.getScene().broadcastPacket(new PacketSceneEntityAppearNotify(player));
    }

    private static boolean hasPermission(
            Player sender, Player targetPlayer, String permission, String permissionTargeted) {
        if (sender == null) return true;
        var account = sender.getAccount();
        String required = targetPlayer != sender ? permissionTargeted : permission;
        if (account != null && account.hasPermission(required)) return true;
        CommandOutput.sendTranslatedMessage(sender, "commands.generic.permission_error");
        return false;
    }
}
