package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import emu.grasscutter.utils.Utils;
import java.util.List;

@Command(
        label = "sgv",
        aliases = {"serverglobalvalue"},
        usage = "<serverGlobalValue> <integerValue>")
public final class SgvCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.size() != 2) {
            this.sendUsageMessage(sender);
            return;
        }

        final float value;
        try {
            value = Integer.parseInt(args.get(1));
        } catch (NumberFormatException ignored) {
            this.sendUsageMessage(sender);
            return;
        }

        String name = args.get(0);
        targetPlayer.sendPacket(
                new PacketServerGlobalValueChangeNotify(
                        targetPlayer.getTeamManager().getEntity().getId(), name, value));
        CommandHandler.sendMessage(targetPlayer, String.valueOf(Utils.abilityHash(name)));
        CommandHandler.sendMessage(targetPlayer, "Changed Server Global Value for " + name);
        if (name.equalsIgnoreCase("SGV_PlayerTeam_Phlogiston")) {
            targetPlayer.setPhlogistonValue(value);
        }
    }
}
