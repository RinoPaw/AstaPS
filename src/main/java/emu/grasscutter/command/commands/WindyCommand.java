package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketWindSeedClientNotify;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "windy",
        permission = "server.windy",
        targetRequirement = Command.TargetRequirement.PLAYER)
public final class WindyCommand implements PicocliCommandHandler {

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "windy")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<lua>")
        private String lua;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            Path fullPath = Paths.get(".").toAbsolutePath().normalize().resolve("lua").resolve(lua);
            try {
                targetPlayer.sendPacket(new PacketWindSeedClientNotify(Files.readAllBytes(fullPath)));
                CommandHandler.sendMessage(sender, "Read BYTECODE from Lua script: " + fullPath);
            } catch (IOException e) {
                CommandHandler.sendMessage(sender, "Error reading Lua script: " + e.getMessage());
            }
        }
    }
}
