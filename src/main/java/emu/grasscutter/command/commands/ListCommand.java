package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import java.util.Map;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "list",
        aliases = {"players"},
        targetRequirement = Command.TargetRequirement.NONE)
public final class ListCommand implements CommandHandler {
    private enum Mode {
        UID
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Args(sender));
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        return commandLine;
    }

    @CommandLine.Command(name = "list")
    private static final class Args implements Runnable {
        private final Player sender;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[uid]")
        private Mode mode;

        private Args(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            Map<Integer, Player> playersMap = Grasscutter.getGameServer().getPlayers();
            boolean includeUid = mode == Mode.UID;

            CommandOutput.sendMessage(sender, translate(sender, "commands.list.success", playersMap.size()));
            if (playersMap.isEmpty()) return;

            String players =
                    playersMap.values().stream()
                            .map(
                                    player -> {
                                        if (!includeUid) return player.getNickname();
                                        if (sender != null) {
                                            return player.getNickname()
                                                    + " <color=green>("
                                                    + player.getUid()
                                                    + ")</color>";
                                        }
                                        return player.getNickname() + " (" + player.getUid() + ")";
                                    })
                            .reduce((left, right) -> left + ", " + right)
                            .orElse("");
            CommandOutput.sendMessage(sender, players);
        }
    }
}
