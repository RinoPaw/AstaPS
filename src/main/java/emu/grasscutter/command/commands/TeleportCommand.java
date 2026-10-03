package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandHelpers;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "teleport",
        aliases = {"tp"},
        targetRequirement = Command.TargetRequirement.NONE)
public final class TeleportCommand implements PicocliCommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender, targetPlayer));
        commandLine.addSubcommand("pos", new PositionTeleport(sender, targetPlayer));
        commandLine.addSubcommand("scene", new SceneTeleport(sender, targetPlayer));
        commandLine.addSubcommand("all", new TeleportAll(sender, targetPlayer));
        return commandLine;
    }

    private static Player resolveTarget(Player sender, Player targetPlayer) {
        if (targetPlayer != null) return targetPlayer;
        if (sender != null) return sender;

        var gameServer = Grasscutter.getGameServer();
        if (gameServer == null) {
            return null;
        }

        var onlinePlayers =
                gameServer.getPlayers().values().stream().filter(Player::isOnline).toList();
        if (onlinePlayers.size() == 1) {
            return onlinePlayers.get(0);
        }

        if (onlinePlayers.isEmpty()) {
            CommandHandler.sendMessage(null, "No online player is available for teleport.");
        } else {
            CommandHandler.sendMessage(
                    null,
                    "Multiple players are online; add @UID to the command or use target @UID first.");
        }
        return null;
    }

    @CommandLine.Command(name = "teleport")
    private final class Root implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[pointId]")
        private Integer pointId;

        @Parameters(index = "1", arity = "0..1", paramLabel = "[sceneId]")
        private Integer sceneId;

        private Root(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (pointId == null) {
                TeleportCommand.this.sendUsageMessage(sender);
                return;
            }
            Player resolvedTarget = resolveTarget(sender, targetPlayer);
            if (resolvedTarget == null) return;
            teleportToScenePoint(sender, resolvedTarget, pointId, sceneId);
        }
    }

    private static void teleportToScenePoint(
            Player sender, Player targetPlayer, int pointId, Integer requestedSceneId) {
        if (!hasPermission(sender, targetPlayer, "player.teleport", "player.teleport.others")) return;

        int sceneId = requestedSceneId == null ? targetPlayer.getSceneId() : requestedSceneId;
        var entry = GameData.getScenePointEntryById(sceneId, pointId);
        if (entry == null || entry.getPointData() == null) {
            CommandHandler.sendMessage(
                    sender, "Scene point " + sceneId + ":" + pointId + " does not exist.");
            return;
        }

        var point = entry.getPointData();
        Position sourcePos = point.getTranPos() != null ? point.getTranPos() : point.getPos();
        if (sourcePos == null) {
            CommandHandler.sendMessage(
                    sender, "Scene point " + sceneId + ":" + pointId + " has no position.");
            return;
        }

        // Command teleport intentionally ignores unlockedScenePoints and forceLockedScenePoints.
        // This is required for testing locked waypoints/statues without mutating their unlock state.
        Position destination = new Position(sourcePos);
        Position pointRot = point.getTranRot() != null ? point.getTranRot() : point.getRot();
        if (pointRot != null) {
            targetPlayer.getRotation().set(pointRot);
        }

        boolean transferred =
                targetPlayer
                        .getWorld()
                        .transferPlayerToScene(
                                targetPlayer, sceneId, TeleportType.COMMAND, destination);
        if (!transferred) {
            CommandHandler.sendMessage(
                    sender, "Failed to teleport to scene point " + sceneId + ":" + pointId + ".");
            return;
        }

        CommandHandler.sendMessage(
                sender,
                "Teleported "
                        + targetPlayer.getNickname()
                        + " to scene point "
                        + sceneId
                        + ":"
                        + pointId
                        + ".");
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
            Player resolvedTarget = resolveTarget(sender, targetPlayer);
            if (resolvedTarget == null) return;
            if (!hasPermission(sender, resolvedTarget, "player.teleport", "player.teleport.others")) return;

            Position basePosition = new Position(resolvedTarget.getPosition());
            Position rotation = new Position(resolvedTarget.getRotation());
            Position destination;
            try {
                destination = CommandHelpers.parsePosition(x, y, z, basePosition, rotation);
            } catch (NumberFormatException ignored) {
                CommandHandler.sendMessage(
                        sender, translate(sender, "commands.teleport.invalid_position"));
                return;
            }

            int destinationScene = sceneId == null ? resolvedTarget.getSceneId() : sceneId;
            boolean transferred =
                    resolvedTarget
                            .getWorld()
                            .transferPlayerToScene(
                                    resolvedTarget,
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
                            resolvedTarget.getNickname(),
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
            Player resolvedTarget = resolveTarget(sender, targetPlayer);
            if (resolvedTarget == null) return;
            if (!hasPermission(sender, resolvedTarget, "player.teleport", "player.teleport.others")) return;

            var scene = resolvedTarget.getWorld().getSceneById(sceneId);
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
            if (config.born_rot != null) resolvedTarget.getRotation().set(config.born_rot);
            boolean transferred =
                    resolvedTarget
                            .getWorld()
                            .transferPlayerToScene(
                                    resolvedTarget, sceneId, TeleportType.COMMAND, destination);
            if (!transferred) {
                CommandHandler.sendMessage(sender, "Failed to enter scene " + sceneId + ".");
                return;
            }

            var pos = resolvedTarget.getPosition();
            var rot = resolvedTarget.getRotation();
            CommandHandler.sendTranslatedMessage(
                    sender,
                    "commands.position.success",
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    rot.getX(),
                    rot.getY(),
                    rot.getZ(),
                    resolvedTarget.getSceneId());
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
            Player resolvedTarget = resolveTarget(sender, targetPlayer);
            if (resolvedTarget == null) return;
            if (!hasPermission(sender, resolvedTarget, "player.tpall", "player.tpall.others")) return;
            if (!resolvedTarget.getWorld().isMultiplayer()) {
                CommandHandler.sendMessage(sender, translate(sender, "commands.teleportAll.error"));
                return;
            }

            for (Player player : resolvedTarget.getWorld().getPlayers()) {
                if (player.equals(resolvedTarget)) continue;
                player.getWorld()
                        .transferPlayerToScene(
                                player,
                                resolvedTarget.getSceneId(),
                                TeleportType.COMMAND,
                                resolvedTarget.getPosition());
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
