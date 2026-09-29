package emu.grasscutter.game.player;

import emu.grasscutter.BuildConfig;

/** Keeps the per-login notice state current without showing server branding in the client. */
public final class EntryNotice {
    private EntryNotice() {}

    public static void sendOnce(Player player) {
        if (player == null || player.isEntryNoticeChecked()) return;
        player.setEntryNoticeChecked(true);
        player.setPendingWelcomeNotice(false);
        player.setLastSeenBuildHash(BuildConfig.GIT_HASH);
    }
}
