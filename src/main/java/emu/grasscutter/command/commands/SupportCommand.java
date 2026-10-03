package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamAbilityToggle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Toggles team-bar support buttons backed by team abilities. */
@Command(
        label = "support",
        aliases = {"assist"},
        usage = {"<pierro|valeriy|blast|all> [on|off]"},
        permission = "player.support",
        permissionTargeted = "player.support.others")
public final class SupportCommand implements CommandHandler {
    private static final Map<String, String> BUTTONS = new LinkedHashMap<>();

    static {
        BUTTONS.put("pierro", "Level_Ronova_SupportSkillHandler");
        BUTTONS.put("valeriy", "SkillObj_SupportSkill_Valeriy_TeamListener");
        BUTTONS.put("blast", "SkillObj_SupportSkill_Laodai_TeamListener");
    }

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.isEmpty()) {
            this.sendUsageMessage(sender);
            return;
        }

        String which = args.get(0).toLowerCase();
        List<String> abilities;
        if (which.equals("all")) {
            abilities = List.copyOf(BUTTONS.values());
        } else if (BUTTONS.containsKey(which)) {
            abilities = List.of(BUTTONS.get(which));
        } else {
            this.sendUsageMessage(sender);
            return;
        }

        boolean enable;
        if (args.size() < 2) {
            enable = !abilities.stream().allMatch(a -> TeamAbilityToggle.isOn(targetPlayer, a));
        } else {
            switch (args.get(1).toLowerCase()) {
                case "on" -> enable = true;
                case "off" -> enable = false;
                default -> {
                    this.sendUsageMessage(sender);
                    return;
                }
            }
        }

        TeamAbilityToggle.set(targetPlayer, abilities, enable);
        CommandHandler.sendMessage(
                sender,
                (enable ? "Enabled support button(s): " : "Disabled support button(s): ") + which + ".");
    }
}
