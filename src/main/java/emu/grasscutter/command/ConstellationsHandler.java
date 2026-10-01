package emu.grasscutter.command;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.commands.Element;
import emu.grasscutter.command.commands.ResetConstCommand;
import emu.grasscutter.command.commands.SetConstCommand;
import emu.grasscutter.game.player.Player;

public class ConstellationsHandler {
    public static void change(Player targetPlayer, Element element, int constellation) {
        try {
            new ResetConstCommand().createCommandLine(null, targetPlayer).execute();
            new SetConstCommand()
                    .createCommandLine(null, targetPlayer)
                    .execute(String.valueOf(constellation));
        } catch (Exception e) {
            Grasscutter.getLogger().info("ConstellationHandler error");
        }
    }
}
