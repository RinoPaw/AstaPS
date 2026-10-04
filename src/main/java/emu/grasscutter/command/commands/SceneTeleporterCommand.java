package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.data.SceneConfig;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import java.util.List;

@Command(
        label = "sceneteleporter",
        aliases = {"sc", "scene", "sceneteleport"},
        usage = "<sceneId>",
        permission = "player.teleport",
        permissionTargeted = "player.teleport.others",
        targetRequirement = Command.TargetRequirement.ONLINE)
public final class SceneTeleporterCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.size() != 1) {
            this.sendUsageMessage(sender);
            return;
        }

        final int sceneId;
        try {
            sceneId = Integer.parseInt(args.get(0));
        } catch (NumberFormatException ignored) {
            CommandHandler.sendMessage(sender, "Teleportation failed: invalid scene ID.");
            return;
        }

        Scene newScene = targetPlayer.getWorld().getSceneById(sceneId);
        if (newScene == null) {
            CommandHandler.sendMessage(sender, "Teleportation failed: scene does not exist.");
            return;
        }

        SceneConfig config = newScene.getScriptManager().getConfig();
        if (config == null) {
            CommandHandler.sendMessage(
                    sender,
                    "Teleportation failed: the scene has no configured spawn position; use teleport instead.");
            return;
        }

        Position pos = config.born_pos;
        targetPlayer.getRotation().set(config.born_rot);
        boolean result =
                targetPlayer
                        .getWorld()
                        .transferPlayerToScene(targetPlayer, sceneId, TeleportType.COMMAND, pos);
        if (!result) {
            CommandHandler.sendMessage(sender, "Teleportation failed.");
            return;
        }

        Position teleportedPos = targetPlayer.getPosition();
        Position teleportedRot = targetPlayer.getRotation();
        CommandHandler.sendMessage(sender, "Teleportation was successful!");
        CommandHandler.sendTranslatedMessage(
                sender,
                "commands.position.success",
                teleportedPos.getX(),
                teleportedPos.getY(),
                teleportedPos.getZ(),
                teleportedRot.getX(),
                teleportedRot.getY(),
                teleportedRot.getZ(),
                targetPlayer.getSceneId());
    }
}
