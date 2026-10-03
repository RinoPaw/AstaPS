package emu.grasscutter.command;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.commands.ConstellationCommand;
import emu.grasscutter.command.commands.Element;
import emu.grasscutter.game.player.Player;

public class ConstellationsHandler {
    public static void change(Player targetPlayer, Element element, int constellation) {
        try {
            var command = new ConstellationCommand();
            command.createCommandLine(null, targetPlayer).execute("reset");
            command.createCommandLine(null, targetPlayer).execute("set", String.valueOf(constellation));
        } catch (Exception e) {
            Grasscutter.getLogger().info("ConstellationHandler error");
        }
    }
}
