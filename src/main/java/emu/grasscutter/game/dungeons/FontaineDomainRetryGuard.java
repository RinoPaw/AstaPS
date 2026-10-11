package emu.grasscutter.game.dungeons;

import emu.grasscutter.game.player.Player;

/** Limits the observed 7.1 retry CmdId to successfully claimed Fontaine weapon domains. */
public final class FontaineDomainRetryGuard {
    private FontaineDomainRetryGuard() {}

    public static boolean mayRestart(Player player, byte[] payload) {
        if (player == null || player.getScene() == null || payload == null) return false;
        var scene = player.getScene();
        var manager = scene.getDungeonManager();
        if (manager == null || manager.getDungeonData() == null) return false;
        synchronized (manager) {
            return isEligible(
                    manager.getDungeonData().getId(),
                    scene.getId(),
                    manager.isFinishedSuccessfully(),
                    manager.getRewardedPlayersForClaims().contains(player.getUid()),
                    payload.length);
        }
    }

    static boolean isEligible(
            int dungeonId, int sceneId, boolean finished, boolean claimed, int payloadLength) {
        // 7.1 client opcode 9381 was observed only for scene 40771 / dungeon 4471.
        // Do not route a possibly aliased opcode into other dungeon types.
        return dungeonId >= 4470
                && dungeonId <= 4473
                && sceneId == 40770 + (dungeonId - 4470)
                && finished
                && claimed
                && payloadLength == 0;
    }
}
