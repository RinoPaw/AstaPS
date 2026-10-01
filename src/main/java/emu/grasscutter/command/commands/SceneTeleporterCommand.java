package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "sceneteleporter",
        aliases = {"sc", "scene", "sceneteleport"},
        permission = "player.teleport",
        permissionTargeted = "player.teleport.others",
        targetRequirement = Command.TargetRequirement.ONLINE)
public final class SceneTeleporterCommand implements PicocliCommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "sceneteleporter")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<sceneId>")
        private int sceneId;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            var scene = targetPlayer.getWorld().getSceneById(sceneId);
            if (scene == null) {
                CommandHandler.sendMessage(sender, "Scene " + sceneId + " does not exist.");
                return;
            }

            var config = scene.getScriptManager().getConfig();
            if (config == null || config.born_pos == null) {
                CommandHandler.sendMessage(
                        sender,
                        "Scene " + sceneId + " has no spawn position; use teleport with coordinates.");
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
}
