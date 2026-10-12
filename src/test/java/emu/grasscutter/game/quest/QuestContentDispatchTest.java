package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuestContentDispatchTest {
    @Test
    void aBadQuestCannotStarveLaterMatchingQuests() {
        var visited = new ArrayList<Integer>();
        var failed = new ArrayList<Integer>();
        QuestContentDispatch.forEachCandidate(
                List.of(35101, 35203, 35302),
                questId -> {
                    if (questId == 35203) throw new IllegalArgumentException("bad quest data");
                    visited.add(questId);
                },
                (questId, error) -> {
                    assertInstanceOf(IllegalArgumentException.class, error);
                    failed.add(questId);
                });
        assertEquals(List.of(35101, 35302), visited);
        assertEquals(List.of(35203), failed);
    }

    @Test
    void malformedAcceptConditionDoesNotBlockFollowingChapterHandoff() {
        var accepted = new ArrayList<Integer>();
        var errors = new ArrayList<Integer>();
        QuestContentDispatch.forEachCandidate(
                List.of(35502, 36101, 35302),
                subId -> {
                    if (subId == 35502) {
                        throw new IllegalStateException("incomplete acceptance data");
                    }
                    accepted.add(subId);
                },
                (subId, error) -> errors.add(subId));
        assertEquals(List.of(36101, 35302), accepted);
        assertEquals(List.of(35502), errors);
    }

    @Test
    void aHealthyBatchRunsEveryCandidateWithoutError() {
        var processed = new ArrayList<Integer>();
        QuestContentDispatch.forEachCandidate(
                List.of(35309, 35310, 35311),
                processed::add,
                (id, error) -> fail("unexpected failure for " + id, error));
        assertEquals(List.of(35309, 35310, 35311), processed);
    }
}
