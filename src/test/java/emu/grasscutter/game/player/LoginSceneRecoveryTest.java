package emu.grasscutter.game.player;

import static emu.grasscutter.game.player.LoginSceneRecovery.Action.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.game.props.SceneType;
import org.junit.jupiter.api.Test;

final class LoginSceneRecoveryTest {
    @Test
    void interruptedFontaineDomainMustNotLoadWithoutDungeonManager() {
        // A saved 40773 is a scene, not a restorable DungeonManager or challenge.
        assertEquals(RETURN_TO_WORLD,
                LoginSceneRecovery.choose(SceneType.SCENE_DUNGEON, false, false));
    }

    @Test
    void otherTransientDungeonsAndTpsScenesFollowSamePolicy() {
        assertEquals(RETURN_TO_WORLD,
                LoginSceneRecovery.choose(SceneType.SCENE_DUNGEON, true, false));
        assertEquals(RETURN_TO_WORLD,
                LoginSceneRecovery.choose(SceneType.SCENE_ROOM, true, false));
        assertEquals(RETURN_TO_WORLD,
                LoginSceneRecovery.choose(null, false, false));
    }

    @Test
    void worldRoomAndActivityScenesKeepExistingLoginLifecycle() {
        assertEquals(KEEP, LoginSceneRecovery.choose(SceneType.SCENE_WORLD, false, false));
        assertEquals(KEEP, LoginSceneRecovery.choose(SceneType.SCENE_ROOM, false, false));
        assertEquals(KEEP, LoginSceneRecovery.choose(SceneType.SCENE_ACTIVITY, false, false));
    }

    @Test
    void homeReturnRejectsAnotherTransientScene() {
        assertEquals(RETURN_FROM_HOME,
                LoginSceneRecovery.choose(SceneType.SCENE_HOME_WORLD, false, true));
        assertEquals(3,
                LoginSceneRecovery.safeHomeDestination(40773, SceneType.SCENE_DUNGEON));
        assertEquals(3,
                LoginSceneRecovery.safeHomeDestination(0, null));
        assertEquals(3,
                LoginSceneRecovery.safeHomeDestination(99999, null));
        assertEquals(3,
                LoginSceneRecovery.safeHomeDestination(3, SceneType.SCENE_WORLD));
        assertEquals(4,
                LoginSceneRecovery.safeHomeDestination(4, SceneType.SCENE_WORLD));
    }

    @Test
    void aRecoveredLoginCanBeRepeatedWithoutFurtherRedirects() {
        assertEquals(RETURN_TO_WORLD,
                LoginSceneRecovery.choose(SceneType.SCENE_DUNGEON, false, false));
        assertEquals(KEEP,
                LoginSceneRecovery.choose(SceneType.SCENE_WORLD, false, false));
    }
}
