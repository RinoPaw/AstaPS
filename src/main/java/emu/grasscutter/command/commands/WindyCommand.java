package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketWindSeedUID;
import emu.grasscutter.server.packet.send.PacketWindy;
import java.util.Set;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(label = "windy")
public final class WindyCommand implements CommandHandler {
    private static final Set<Double> SUPPORTED_SPEEDS =
            Set.of(0.1, 0.2, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0);

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("fps", new Packaged(sender, targetPlayer, "fps", "FPS script sent."));
        commandLine.addSubcommand("speed", new Speed(sender, targetPlayer));
        commandLine.addSubcommand(
                "hide-ui", new Packaged(sender, targetPlayer, "HideUI", "UI hidden successfully."));
        commandLine.addSubcommand(
                "no-fog", new Packaged(sender, targetPlayer, "fog", "No-fog script sent."));
        commandLine.addSubcommand(
                "remove-censorship",
                new Packaged(
                        sender,
                        targetPlayer,
                        "RemoveCensorship",
                        "Remove-censorship script sent."));
        commandLine.addSubcommand("uid", new Uid(sender, targetPlayer));
        return commandLine;
    }

    @CommandLine.Command(name = "windy")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            WindyCommand.this.sendUsageMessage(sender);
        }
    }

    @CommandLine.Command(name = "packaged")
    private static final class Packaged implements Runnable {
        private final Player sender;
        private final Player targetPlayer;
        private final String script;
        private final String success;

        private Packaged(Player sender, Player targetPlayer, String script, String success) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
            this.script = script;
            this.success = success;
        }

        @Override
        public void run() {
            if (!hasPermission(sender, targetPlayer)) return;
            targetPlayer.sendPacket(new PacketWindy(script));
            CommandOutput.sendMessage(sender, success);
        }
    }

    @CommandLine.Command(name = "speed")
    private final class Speed implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<speed>")
        private double speed;

        private Speed(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!hasPermission(sender, targetPlayer)) return;
            if (!SUPPORTED_SPEEDS.contains(speed)) {
                WindyCommand.this.sendUsageMessage(sender);
                return;
            }
            String text = Double.toString(speed);
            targetPlayer.sendPacket(new PacketWindy("GameSpeed/speed" + text));
            CommandOutput.sendMessage(sender, "GameSpeed changed to " + text + " successfully!");
        }
    }

    @CommandLine.Command(name = "uid")
    private static final class Uid implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private Uid(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!hasPermission(sender, targetPlayer)) return;
            targetPlayer.sendPacket(new PacketWindSeedUID());
            CommandOutput.sendMessage(sender, "Loaded the packaged UID watermark script.");
        }
    }

    private static boolean hasPermission(Player sender, Player targetPlayer) {
        if (sender == null) return true;
        var account = sender.getAccount();
        String required = targetPlayer != sender ? "player.windy.others" : "player.windy";
        if (account != null && account.hasPermission(required)) return true;
        CommandOutput.sendTranslatedMessage(sender, "commands.generic.permission_error");
        return false;
    }
}
