package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamAbilityToggle;
import java.util.List;

/** Toggles Il Pierro's support button from the 7.1 Ronova fight. */
@Command(
        label = "pierro",
        aliases = {"ronova"},
        usage = {"[on|off]"},
        permission = "player.pierro",
        permissionTargeted = "player.pierro.others")
public final class PierroCommand implements CommandHandler {
    private static final String SUPPORT_SKILL = "Level_Ronova_SupportSkillHandler";

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        boolean enable;
        if (args.isEmpty()) {
            enable = !TeamAbilityToggle.isOn(targetPlayer, SUPPORT_SKILL);
        } else {
            switch (args.get(0).toLowerCase()) {
                case "on" -> enable = true;
                case "off" -> enable = false;
                default -> {
                    this.sendUsageMessage(sender);
                    return;
                }
            }
        }

        TeamAbilityToggle.set(targetPlayer, List.of(SUPPORT_SKILL), enable);
        CommandHandler.sendMessage(
                sender, enable ? "Enabled Pierro support button." : "Disabled Pierro support button.");
    }
}
