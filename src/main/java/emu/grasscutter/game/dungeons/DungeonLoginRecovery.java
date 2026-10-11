package emu.grasscutter.game.dungeons;

import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.Position;
import java.util.function.IntPredicate;

/** Migrate an orphaned saved dungeon to a safe open-world scene before world creation. */
public final class DungeonLoginRecovery {
    private DungeonLoginRecovery() {}

    static boolean hasValidWorldEntry(
            int candidateId, Position returnPosition, IntPredicate isWorld) {
        return candidateId > 0 && validPosition(returnPosition) && isWorld.test(candidateId);
    }

    static int chooseWorldScene(int candidateId, Position returnPosition, IntPredicate isWorld) {
        return hasValidWorldEntry(candidateId, returnPosition, isWorld) ? candidateId : 3;
    }

    private static boolean validPosition(Position pos) {
        return pos != null
                && Float.isFinite(pos.getX())
                && Float.isFinite(pos.getY())
                && Float.isFinite(pos.getZ())
                && !(pos.getX() == 0 && pos.getY() == 0 && pos.getZ() == 0);
    }

    public static void recover(Player player) {
        if (player == null) return;
        var savedScene = GameData.getSceneDataMap().get(player.getSceneId());
        if (savedScene == null || savedScene.getSceneType() != SceneType.SCENE_DUNGEON) {
            return;
        }

        int oldScene = player.getSceneId();
        int requestedWorld = player.getDungeonReturnSceneId();
        Position entryPosition = player.getDungeonReturnPosition();
        boolean hasEntry = hasValidWorldEntry(requestedWorld, entryPosition,
                id -> {
                    var data = GameData.getSceneDataMap().get(id);
                    return data != null && data.getSceneType() == SceneType.SCENE_WORLD;
                });
        int newScene = hasEntry ? requestedWorld : 3;

        player.setSceneId(newScene);
        player.getPosition().set(hasEntry ? entryPosition : GameConstants.START_POSITION);
        player.setDungeonReturnSceneId(0);
        player.setDungeonReturnPosition(null);
        Grasscutter.getLogger().info(
                "Dungeon login recovery uid={} orphanScene={} -> worldScene={} returnEntry={}",
                player.getUid(), oldScene, newScene, hasEntry);
    }
}
