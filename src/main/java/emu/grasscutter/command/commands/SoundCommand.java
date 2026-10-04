package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.server.packet.send.PacketScenePlayerSoundNotify;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "sound",
        aliases = {"audio"},
        permission = "player.sound",
        permissionTargeted = "player.sound.others")
public final class SoundCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "sound")
    private final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<sound>")
        private String soundName;

        @Parameters(index = "1..3", arity = "0..3", paramLabel = "[x y z]")
        private float[] coordinates = new float[0];

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (coordinates.length != 0 && coordinates.length != 3) {
                SoundCommand.this.sendUsageMessage(sender);
                return;
            }

            Position playPosition =
                    coordinates.length == 3
                            ? new Position(coordinates[0], coordinates[1], coordinates[2])
                            : targetPlayer.getPosition();
            targetPlayer
                    .getScene()
                    .broadcastPacket(new PacketScenePlayerSoundNotify(playPosition, soundName, 1));
        }
    }
}
