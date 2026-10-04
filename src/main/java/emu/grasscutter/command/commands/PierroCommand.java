package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamAbilityToggle;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

/** Toggles Il Pierro's support button from the 7.1 Ronova fight. */
@Command(
        label = "pierro",
        aliases = {"ronova"},
        permission = "player.pierro",
        permissionTargeted = "player.pierro.others")
public final class PierroCommand implements CommandHandler {
    private static final String SUPPORT_SKILL = "Level_Ronova_SupportSkillHandler";

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "pierro")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[on|off]")
        private String state;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            boolean enable;
            if (state == null) {
                enable = !TeamAbilityToggle.isOn(targetPlayer, SUPPORT_SKILL);
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

            TeamAbilityToggle.set(targetPlayer, List.of(SUPPORT_SKILL), enable);
            CommandOutput.sendTranslatedMessage(
                    sender, enable ? "commands.pierro.on" : "commands.pierro.off");
        }
    }
}
