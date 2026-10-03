package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandHelpers;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(label = "teleport", aliases = {"tp"})
public final class TeleportCommand implements PicocliCommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("pos", new PositionTeleport(sender, targetPlayer));
        commandLine.addSubcommand("scene", new SceneTeleport(sender, targetPlayer));
        commandLine.addSubcommand("all", new TeleportAll(sender, targetPlayer));
        return commandLine;
    }

    @CommandLine.Command(name = "teleport")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            TeleportCommand.this.sendUsageMessage(sender);
        }
    }

    @CommandLine.Command(name = "pos")
    private static final class PositionTeleport implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<x>")
        private String x;

        @Parameters(index = "1", paramLabel = "<y>")
        private String y;

        @Parameters(index = "2", paramLabel = "<z>")
        private String z;

        @Parameters(index = "3", arity = "0..1", paramLabel = "[sceneId]")
        private Integer sceneId;

        private PositionTeleport(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!hasPermission(sender, targetPlayer, "player.teleport", "player.teleport.others")) return;

            Position basePosition = new Position(targetPlayer.getPosition());
            Position rotation = new Position(targetPlayer.getRotation());
            Position destination;
            try {
                destination = CommandHelpers.parsePosition(x, y, z, basePosition, rotation);
            } catch (NumberFormatException ignored) {
                CommandHandler.sendMessage(
                        sender, translate(sender, "commands.teleport.invalid_position"));
                return;
            }

            int destinationScene = sceneId == null ? targetPlayer.getSceneId() : sceneId;
            boolean transferred =
                    targetPlayer
                            .getWorld()
                            .transferPlayerToScene(
                                    targetPlayer,
                                    destinationScene,
                                    TeleportType.COMMAND,
                                    destination);
            if (!transferred) {
                CommandHandler.sendMessage(sender, translate(sender, "commands.teleport.exists_error"));
                return;
            }

            CommandHandler.sendMessage(
                    sender,
                    translate(
                            sender,
                            "commands.teleport.success",
                            targetPlayer.getNickname(),
                            destination.getX(),
                            destination.getY(),
                            destination.getZ(),
                            destinationScene));
        }
    }

    @CommandLine.Command(name = "scene")
    private static final class SceneTeleport implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<sceneId>")
        private int sceneId;

        private SceneTeleport(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!hasPermission(sender, targetPlayer, "player.teleport", "player.teleport.others")) return;

            var scene = targetPlayer.getWorld().getSceneById(sceneId);
            if (scene == null) {
                CommandHandler.sendMessage(sender, "Scene " + sceneId + " does not exist.");
                return;
            }

            var config = scene.getScriptManager().getConfig();
            if (config == null || config.born_pos == null) {
                CommandHandler.sendMessage(
                        sender,
                        "Scene " + sceneId + " has no spawn position; use teleport pos with coordinates.");
                return;
            }

            Position destination = config.born_pos;
            if (config.born_rot != null) targetPlayer.getRotation().set(config.born_rot);
            boolean transferred =
                    targetPlayer
                            .getWorld()
                            .transferPlayerToScene(
                                    targetPlayer, sceneId, TeleportType.COMMAND, destination);
            if (!transferred) {
                CommandHandler.sendMessage(sender, "Failed to enter scene " + sceneId + ".");
                return;
            }

            var pos = targetPlayer.getPosition();
            var rot = targetPlayer.getRotation();
            CommandHandler.sendTranslatedMessage(
                    sender,
                    "commands.position.success",
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    rot.getX(),
                    rot.getY(),
                    rot.getZ(),
                    targetPlayer.getSceneId());
        }
    }

    @CommandLine.Command(name = "all")
    private static final class TeleportAll implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private TeleportAll(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!hasPermission(sender, targetPlayer, "player.tpall", "player.tpall.others")) return;
            if (!targetPlayer.getWorld().isMultiplayer()) {
                CommandHandler.sendMessage(sender, translate(sender, "commands.teleportAll.error"));
                return;
            }

            for (Player player : targetPlayer.getWorld().getPlayers()) {
                if (player.equals(targetPlayer)) continue;
                player.getWorld()
                        .transferPlayerToScene(
                                player,
                                targetPlayer.getSceneId(),
                                TeleportType.COMMAND,
                                targetPlayer.getPosition());
            }

            CommandHandler.sendMessage(sender, translate(sender, "commands.teleportAll.success"));
        }
    }

    private static boolean hasPermission(
            Player sender, Player targetPlayer, String permission, String permissionTargeted) {
        if (sender == null) return true;
        var account = sender.getAccount();
        String required = targetPlayer != sender ? permissionTargeted : permission;
        if (account != null && account.hasPermission(required)) return true;
        CommandHandler.sendTranslatedMessage(sender, "commands.generic.permission_error");
        return false;
    }
}
