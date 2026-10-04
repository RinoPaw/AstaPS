package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketWindy;
import java.util.List;
import java.util.Set;

@Command(
        label = "GameSpeed",
        usage = "<0.1|0.2|0.5|0.75|1.0|1.5|2.0|3.0>",
        aliases = {"speed"},
        permission = "player.windy",
        permissionTargeted = "player.windy.others")
public final class GameSpeedCommand implements CommandHandler {
    private static final Set<String> VALID_SPEEDS =
            Set.of("0.1", "0.2", "0.5", "0.75", "1.0", "1.5", "2.0", "3.0");

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.size() != 1 || !VALID_SPEEDS.contains(args.get(0))) {
            this.sendUsageMessage(sender);
            return;
        }

        String speed = args.get(0);
        targetPlayer.sendPacket(new PacketWindy("GameSpeed/speed" + speed));
        CommandHandler.sendMessage(sender, "GameSpeed changed to " + speed + " successfully!");
    }
}
