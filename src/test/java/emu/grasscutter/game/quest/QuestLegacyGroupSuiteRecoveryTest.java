package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

final class QuestLegacyGroupSuiteRecoveryTest {
    private static QuestGroupSuite suite(int scene, int group, int index) {
        return QuestGroupSuite.of().scene(scene).group(group).suite(index).build();
    }

    @Test
    void legacyDuplicateSlimeSuitesRestoreOnlyTheLastActiveVersion() {
        var restored = QuestManager.latestSceneGroupSuites(
                List.of(suite(3, 133003002, 1), suite(3, 133003002, 2),
                        suite(3, 133003136, 1)),
                3);
        assertEquals(2, restored.size());
        assertEquals(2, restored.stream()
                .filter(x -> x.getGroup() == 133003002)
                .findFirst().orElseThrow().getSuite());
    }

    @Test
    void aLegacyZeroResetRemovesTheStaleOverride() {
        var restored = QuestManager.latestSceneGroupSuites(
                List.of(suite(3, 133003002, 2), suite(3, 133003002, 0),
                        suite(3, 133003136, 1)),
                3);
        assertEquals(1, restored.size());
        assertEquals(133003136, restored.get(0).getGroup());
    }

    @Test
    void separateScenesCannotOverwriteEachOthersGroups() {
        var entries = List.of(suite(3, 133003002, 2), suite(4, 133003002, 1));
        assertEquals(2, QuestManager.latestSceneGroupSuites(entries, 3)
                .get(0).getSuite());
        assertEquals(1, QuestManager.latestSceneGroupSuites(entries, 4)
                .get(0).getSuite());
    }
}
