package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketOpenStateChangeNotify;
import java.util.List;

@Command(
        label = "barrier",
        usage = "[on|off|1|0]",
        aliases = {"br", "pb"},
        permission = "player.setprop",
        permissionTargeted = "player.setprop.others")
public final class BarrierCommand implements CommandHandler {
    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.size() > 1) {
            this.sendUsageMessage(sender);
            return;
        }

        String state = args.isEmpty() ? "off" : args.get(0).toLowerCase();
        switch (state) {
            case "on", "1" -> {
                targetPlayer.sendPacket(new PacketOpenStateChangeNotify(48, 0));
                CommandHandler.sendMessage(sender, "Restored barrier");
            }
            case "off", "0" -> {
                targetPlayer.sendPacket(new PacketOpenStateChangeNotify(48, 1));
                CommandHandler.sendMessage(sender, "Removed barrier");
            }
            default -> this.sendUsageMessage(sender);
        }
    }
}
