package emu.grasscutter.command;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.event.game.ReceiveCommandFeedbackEvent;

/** Shared command-output helpers, separate from the command execution contract. */
public final class CommandOutput {
    private CommandOutput() {}

    /** Send command feedback to a player, or to the server console when {@code player} is null. */
    public static void sendMessage(Player player, String message) {
        var event = new ReceiveCommandFeedbackEvent(player, message);
        event.call();
        if (event.isCanceled()) return;

        if (player == null) {
            Grasscutter.getLogger().info(event.getMessage());
            CommandOutputCapture.offer(event.getMessage());
        } else {
            player.dropMessage(event.getMessage().replace("\n\t", "\n\n"));
        }
    }

    public static void sendTranslatedMessage(Player player, String messageKey, Object... args) {
        sendMessage(player, translate(player, messageKey, args));
    }
}
