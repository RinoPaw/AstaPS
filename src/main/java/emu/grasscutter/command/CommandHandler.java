package emu.grasscutter.command;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.game.player.Player;
import picocli.CommandLine;

/** A command whose complete argument grammar is described by picocli. */
public interface CommandHandler {
    /** Compatibility for callers that only emit feedback, including older plugins. */
    static void sendMessage(Player player, String message) {
        CommandOutput.sendMessage(player, message);
    }

    static void sendTranslatedMessage(Player player, String key, Object... arguments) {
        CommandOutput.sendTranslatedMessage(player, key, arguments);
    }

    /** Build this command's picocli tree for one invocation or for completion when both players are null. */
    CommandLine createCommandLine(Player sender, Player targetPlayer);

    /** Build a context-free command tree used as parser/completion/help metadata. */
    default CommandLine createCompletionCommandLine() {
        return createCommandLine(null, null);
    }

    default String getUsageString(Player player) {
        return createCommandLine(player, null).getUsageMessage().stripTrailing();
    }

    default void sendUsageMessage(Player player) {
        CommandOutput.sendMessage(player, getUsageString(player));
    }

    default String getLabel() {
        return getClass().getAnnotation(Command.class).label();
    }

    default String getDescriptionKey() {
        return "commands.%s.description".formatted(getLabel());
    }

    default String getDescriptionString(Player player) {
        return translate(player, getDescriptionKey());
    }
}
