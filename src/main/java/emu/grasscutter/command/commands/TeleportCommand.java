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

@Command(
        label = "teleport",
        aliases = {"tp"},
        permission = "player.teleport",
        permissionTargeted = "player.teleport.others")
public final class TeleportCommand implements PicocliCommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "teleport")
    private static final class Args implements Runnable {
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

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
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
}
