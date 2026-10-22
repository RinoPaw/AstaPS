package emu.grasscutter.game.player;

import static emu.grasscutter.config.Configuration.GAME_INFO;

import emu.grasscutter.BuildConfig;
import emu.grasscutter.Grasscutter;
import java.util.Objects;

/**
 * The message box shown once the player is in the world: a welcome the first time, and afterwards
 * a notice when the server has been rebuilt since the player was last in. The live announcements
 * follow once, 30 seconds later.
 *
 * <p>The client only renders an AntiAddictNotify once it is fully in the world; sent during the
 * login handshake it is swallowed. It used to hang off TowerAllDataReq, whose 7.1 CmdId is not
 * known, so it never went out; PostEnterSceneReq arrives after every scene entry, and this sends
 * at most once per login whichever request gets here first.
 */
public final class EntryNotice {
    static final long BANNER_DELAY_MS = 30_000;

    private EntryNotice() {}

    public static void sendOnce(Player player) {
        if (player == null || player.isEntryNoticeChecked()) return;
        player.setEntryNoticeChecked(true);

        // Diagnostic isolation only: the update modal is normally emitted immediately
        // after PostEnterSceneRsp whenever the build hash changes. Every experiment
        // changes that hash, and the modal may make the client pause while still loading.
        boolean firstWelcome = player.isPendingWelcomeNotice();
        boolean buildChanged = !Objects.equals(player.getLastSeenBuildHash(), BuildConfig.GIT_HASH);
        Grasscutter.getLogger().info(
                "White-load trace EntryNotice uid={} scene={} loadState={} firstWelcome={} "
                        + "buildChanged={} updateModalSuppressed=true",
                player.getUid(), player.getSceneId(), player.getSceneLoadState(),
                firstWelcome, buildChanged);
        if (firstWelcome) player.setPendingWelcomeNotice(false);

        player.setLastSeenBuildHash(BuildConfig.GIT_HASH);

        // The welcome banner goes out once per login, half a minute after the player is in.
        var server = player.getServer();
        if (server != null && server.getAnnouncementSystem() != null) {
            int ticks = (int) Math.ceil(BANNER_DELAY_MS / (double) Math.max(1, GAME_INFO.tickRateMs));
            server.getScheduler()
                    .scheduleDelayedTask(
                            () -> {
                                if (player.isOnline()) server.getAnnouncementSystem().sendActive(player);
                            },
                            Math.max(1, ticks));
        }
    }
}
