package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Diagnostic instrumentation must never claim non-Mondstadt or non-slime mobs. */
final class MondstadtSlimeSpawnTraceTest {
    @Test
    void observesOnlyAnemoAndPyroSlimesInSceneThree() {
        for (int id : new int[]{20011201, 20011202, 20011301, 20010301, 20010401}) {
            assertTrue(Scene.isMondstadtTutorialSlime(3, id), "monster " + id);
            assertFalse(Scene.isMondstadtTutorialSlime(5, id), "scene 5 " + id);
        }
    }

    @Test
    void doesNotIncludeHilichurlsBossesOrOtherElements() {
        for (int id : new int[]{0, 20010101, 20011001, 20040201, 21010201}) {
            assertFalse(Scene.isMondstadtTutorialSlime(3, id), "monster " + id);
        }
    }
}
