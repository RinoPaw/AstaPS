package emu.grasscutter.game.player;

import emu.grasscutter.BuildConfig;

/**
 * Per-login entry hook for optional server announcements.
 *
 * <p>The play/rino branch intentionally suppresses AstaPS's built-in welcome/update dialogs while
 * keeping the entry hook itself available for user-configured announcements.
 */
public final class EntryNotice {
    private EntryNotice() {}

    public static void sendOnce(Player player) {
        if (player == null || player.isEntryNoticeChecked()) return;
        player.setEntryNoticeChecked(true);

        // Keep the persisted bookkeeping current without showing AstaPS's default popup dialogs.
        player.setPendingWelcomeNotice(false);
        player.setLastSeenBuildHash(BuildConfig.GIT_HASH);

        // Custom announcements remain supported; an empty Announcement.json simply sends nothing.
        var server = player.getServer();
        if (server != null && server.getAnnouncementSystem() != null) {
            server.getAnnouncementSystem().sendActive(player);
        }
    }
}
