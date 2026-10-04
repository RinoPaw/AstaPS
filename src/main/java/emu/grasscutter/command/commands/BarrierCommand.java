package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketOpenStateChangeNotify;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "barrier",
        aliases = {"br", "pb"},
        permission = "player.setprop",
        permissionTargeted = "player.setprop.others")
public final class BarrierCommand implements CommandHandler {
    private enum State {
        ON,
        OFF
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Args(sender, targetPlayer));
        commandLine.registerConverter(
                State.class,
                value ->
                        switch (value.toLowerCase()) {
                            case "on", "1" -> State.ON;
                            case "off", "0" -> State.OFF;
                            default -> throw new CommandLine.TypeConversionException(
                                    "Expected on/off or 1/0");
                        });
        return commandLine;
    }

    @CommandLine.Command(name = "barrier")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[on|off]")
        private State state = State.OFF;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            boolean enabled = state == State.ON;
            targetPlayer.sendPacket(new PacketOpenStateChangeNotify(48, enabled ? 0 : 1));
            CommandOutput.sendMessage(sender, enabled ? "Restored barrier" : "Removed barrier");
        }
    }
}
