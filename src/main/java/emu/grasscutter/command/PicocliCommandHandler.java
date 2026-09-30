package emu.grasscutter.command;

import emu.grasscutter.game.player.Player;
import java.util.List;
import picocli.CommandLine;

/** A command whose complete argument grammar is described by picocli. */
public interface PicocliCommandHandler extends CommandHandler {
    /** Build this command's picocli tree for one invocation or for completion when both players are null. */
    CommandLine createCommandLine(Player sender, Player targetPlayer);

    /** Build a context-free command tree used as parser/completion/help metadata. */
    default CommandLine createCompletionCommandLine() {
        return createCommandLine(null, null);
    }

    @Override
    default void execute(Player sender, Player targetPlayer, List<String> args) {
        createCommandLine(sender, targetPlayer).execute(args.toArray(String[]::new));
    }

    @Override
    default String getUsageString(Player player, String... ignored) {
        return createCommandLine(player, null).getUsageMessage().stripTrailing();
    }

    @Override
    default void sendUsageMessage(Player player, String... ignored) {
        CommandHandler.sendMessage(player, getUsageString(player));
    }
}
