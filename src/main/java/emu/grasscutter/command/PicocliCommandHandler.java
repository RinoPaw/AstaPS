package emu.grasscutter.command;

import emu.grasscutter.game.player.Player;
import java.util.List;
import picocli.CommandLine;

/**
 * A command whose argument grammar is described by picocli.
 *
 * <p>{@link CommandMap} still owns AstaPS-wide concerns such as sender/target resolution,
 * permissions, {@code @UID}, the persistent {@code target} command, command events, and threading.
 * Implementations own only the grammar beneath their existing top-level command name.
 *
 * <p>A fresh {@link CommandLine} is created for every invocation because picocli command objects
 * contain mutable parse state. A context-free instance is also used to expose the same grammar to
 * JLine completion.
 */
public interface PicocliCommandHandler extends CommandHandler {
    /** Build this command's picocli tree for one invocation or for completion when both players are null. */
    CommandLine createCommandLine(Player sender, Player targetPlayer);

    /** Build a context-free command tree used only as parser/completion metadata. */
    default CommandLine createCompletionCommandLine() {
        return createCommandLine(null, null);
    }

    @Override
    default void execute(Player sender, Player targetPlayer, List<String> args) {
        createCommandLine(sender, targetPlayer).execute(args.toArray(String[]::new));
    }
}
