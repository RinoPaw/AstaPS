package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamAbilityToggle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

/** Toggles team-bar support buttons backed by team abilities. */
@Command(
        label = "support",
        aliases = {"assist"},
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
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "support")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<pierro|valeriy|blast|all>")
        private String which;

        @Parameters(index = "1", arity = "0..1", paramLabel = "[on|off]")
        private String state;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            String key = which.toLowerCase();
            List<String> abilities;
            if (key.equals("all")) {
                abilities = List.copyOf(BUTTONS.values());
            } else if (BUTTONS.containsKey(key)) {
                abilities = List.of(BUTTONS.get(key));
            } else {
                CommandOutput.sendMessage(sender, "Unknown support button: " + which);
                return;
            }

            boolean enable;
            if (state == null) {
                enable = !abilities.stream().allMatch(a -> TeamAbilityToggle.isOn(targetPlayer, a));
            } else {
                switch (state.toLowerCase()) {
                    case "on" -> enable = true;
                    case "off" -> enable = false;
                    default -> {
                        CommandOutput.sendMessage(sender, "State must be on or off.");
                        return;
                    }
                }
            }

            TeamAbilityToggle.set(targetPlayer, abilities, enable);
            CommandOutput.sendTranslatedMessage(
                    sender, enable ? "commands.support.on" : "commands.support.off", key);
        }
    }
}
