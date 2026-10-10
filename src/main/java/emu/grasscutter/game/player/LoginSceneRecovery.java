package emu.grasscutter.game.player;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.home.GameHome;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.tps.TpsAvatarSystem;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.scripts.ScriptLoader;

/**
 * Resolves saved login locations before a World exists.
 *
 * <p>Only scenes with a self-contained login lifecycle can be restored from sceneId.
 * A dungeon manager, challenge and temporary trial team are runtime state; a plain
 * World.addPlayer cannot reconstruct them after an unclean disconnect or restart.
 */
public final class LoginSceneRecovery {
    static final int DEFAULT_WORLD_SCENE = 3;

    enum Action {
        KEEP,
        RETURN_TO_WORLD,
        RETURN_FROM_HOME
    }

    private LoginSceneRecovery() {}

    static Action choose(SceneType savedType, boolean tpsScene, boolean homeScene) {
        if (savedType == null || savedType == SceneType.SCENE_NONE
                || savedType == SceneType.SCENE_DUNGEON || tpsScene) {
            return Action.RETURN_TO_WORLD;
        }
        return homeScene ? Action.RETURN_FROM_HOME : Action.KEEP;
    }

    static int safeHomeDestination(int previousScene, SceneType previousType) {
        if (previousScene > 0
                && (previousType == SceneType.SCENE_WORLD || previousType == SceneType.SCENE_ROOM)) {
            return previousScene;
        }
        return DEFAULT_WORLD_SCENE;
    }

    public static void prepare(Player player) {
        if (player == null) {
            return;
        }

        int savedId = player.getSceneId();
        var savedData = GameData.getSceneDataMap().get(savedId);
        var savedType = savedData == null ? null : savedData.getSceneType();
        Action action = choose(
                savedType,
                TpsAvatarSystem.isTpsScene(savedData),
                GameHome.HOME_SCENE_IDS.contains(savedId));

        switch (action) {
            case KEEP -> {
                return;
            }
            case RETURN_TO_WORLD -> {
                // Restarting at the origin avoids using dungeon-local coordinates in Teyvat.
                player.setSceneId(DEFAULT_WORLD_SCENE);
                player.getPosition().set(ScriptLoader.getSceneMeta(DEFAULT_WORLD_SCENE).config.born_pos);
                Grasscutter.getLogger().warn(
                        "Login scene recovery uid={} savedScene={} type={} -> world {}; "
                                + "transient dungeon state cannot be resumed",
                        player.getUid(), savedId, savedType, DEFAULT_WORLD_SCENE);
            }
            case RETURN_FROM_HOME -> {
                int previous = player.getPrevScene();
                var previousData = GameData.getSceneDataMap().get(previous);
                var previousType = previousData == null ? null : previousData.getSceneType();
                int destination = safeHomeDestination(previous, previousType);
                Position homeReturnPosition = player.getPrevPosForHome();
                if (destination != previous || homeReturnPosition == null
                        || homeReturnPosition.equals(Position.ZERO)) {
                    homeReturnPosition = ScriptLoader.getSceneMeta(destination).config.born_pos;
                }
                player.setSceneId(destination);
                player.getPosition().set(homeReturnPosition);
            }
        }
    }
}
