package emu.grasscutter.command;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.event.game.ReceiveCommandFeedbackEvent;

/**
 * Unified contract for built-in commands.
 *
 * <p>Command parsing is picocli-based. The static output helpers stay here while the command
 * migration finishes so existing command infrastructure can share the same feedback path.
 */
public interface CommandHandler extends PicocliCommandHandler {
    static void sendMessage(Player player, String message) {
        ReceiveCommandFeedbackEvent event = new ReceiveCommandFeedbackEvent(player, message);
        event.call();
        if (event.isCanceled()) return;

        if (player == null) {
            Grasscutter.getLogger().info(event.getMessage());
        } else {
            player.dropMessage(event.getMessage().replace("\n\t", "\n\n"));
        }
    }

    static void sendTranslatedMessage(Player player, String messageKey, Object... args) {
        sendMessage(player, translate(player, messageKey, args));
    }
}
