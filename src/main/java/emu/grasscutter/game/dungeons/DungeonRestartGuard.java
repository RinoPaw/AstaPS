package emu.grasscutter.game.dungeons;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.SceneType;

/**
 * Shared validation for post-reward dungeon restarts (7.1 CmdId 9381).
 *
 * <p>The per-run reward marker is set only by a successful statue claim. Keeping
 * this check prevents retrying an unclaimed or active run and preserves the
 * existing one-claim-per-run protection. Co-op restart needs its own consent
 * handshake; never move other players based on one member's request.
 */
public final class DungeonRestartGuard {
    private DungeonRestartGuard() {}

    public static boolean mayRestart(Player player, byte[] payload) {
        if (player == null || payload == null || player.getScene() == null) return false;

        var scene = player.getScene();
        if (scene.getSceneType() != SceneType.SCENE_DUNGEON
                || !scene.getPlayers().contains(player)) {
            return false;
        }

        var manager = scene.getDungeonManager();
        if (manager == null || manager.getDungeonData() == null) return false;

        synchronized (manager) {
            return isEligible(
                    manager.getDungeonData().getId(),
                    manager.getDungeonData().getSceneId(),
                    scene.getId(),
                    manager.isTowerDungeon(),
                    manager.isFinishedSuccessfully(),
                    manager.getRewardedPlayersForClaims().contains(player.getUid()),
                    scene.getPlayers().size(),
                    payload.length);
        }
    }

    static boolean isEligible(
            int dungeonId,
            int configuredSceneId,
            int currentSceneId,
            boolean isTower,
            boolean finished,
            boolean claimed,
            int participantCount,
            int payloadLength) {
        return dungeonId > 0
                && configuredSceneId > 0
                && configuredSceneId == currentSceneId
                && !isTower
                && finished
                && claimed
                && participantCount == 1
                && payloadLength == 0;
    }
}
