package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SceneCutsceneGroupsTest {
    @Test
    void completionConsumesOnlyTheRelevantCutsceneOnce() {
        Map<Integer, Set<Integer>> pending = new HashMap<>();
        pending.put(123, new HashSet<>(Set.of(133007227, 133007228)));
        pending.put(124, new HashSet<>(Set.of(133007229)));

        assertEquals(Set.of(133007227, 133007228),
                SceneScriptManager.takeCutsceneGroups(pending, 123));
        assertTrue(SceneScriptManager.takeCutsceneGroups(pending, 123).isEmpty());
        assertEquals(Set.of(133007229),
                SceneScriptManager.takeCutsceneGroups(pending, 124));
    }

    @Test
    void absentCutsceneHasNoQuestEvents() {
        assertTrue(SceneScriptManager.takeCutsceneGroups(new HashMap<>(), 0).isEmpty());
    }
}
