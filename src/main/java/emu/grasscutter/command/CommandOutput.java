package emu.grasscutter.command;

import emu.grasscutter.game.player.Player;

/** Shared command-output helpers, separate from the command execution contract. */
public final class CommandOutput {
    private CommandOutput() {}

    public static void sendMessage(Player player, String message) {
        CommandHandler.sendMessage(player, message);
    }

    public static void sendTranslatedMessage(Player player, String messageKey, Object... args) {
        CommandHandler.sendTranslatedMessage(player, messageKey, args);
    }
}
