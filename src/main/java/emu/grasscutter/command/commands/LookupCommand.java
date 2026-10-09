package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.NameIndex;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

/** Finds the id of anything by part of its name, without leaving the game. */
@Command(
        label = "lookup",
        aliases = {"find", "search"},
        targetRequirement = Command.TargetRequirement.NONE)
public final class LookupCommand implements CommandHandler {
    private static final int LIMIT = 15;

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender));
    }

    @CommandLine.Command(name = "lookup")
    private static final class Args implements Runnable {
        private final Player sender;

        @Parameters(index = "0..*", arity = "1..*", paramLabel = "<name>")
        private String[] words;

        private Args(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            var query = String.join(" ", words);
            var found = NameIndex.search(query, LIMIT);

            if (found.isEmpty()) {
                CommandOutput.sendMessage(sender, "Nothing is called anything like \"" + query + "\".");
                return;
            }

            CommandOutput.sendMessage(
                    sender,
                    found.size() < LIMIT
                            ? found.size()
                                    + " match"
                                    + (found.size() == 1 ? "" : "es")
                                    + " for \""
                                    + query
                                    + "\":"
                            : "First " + LIMIT + " matches for \"" + query + "\":");
            found.forEach(entry -> CommandOutput.sendMessage(sender, "  " + entry));
        }
    }
}
