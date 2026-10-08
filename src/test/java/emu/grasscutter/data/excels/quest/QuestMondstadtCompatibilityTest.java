package emu.grasscutter.data.excels.quest;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuestMondstadtCompatibilityTest {
    private static final Gson GSON = new Gson();
    private static QuestData.QuestAcceptCondition state(int id) {
        return GSON.fromJson(
                "{\"type\":\"QUEST_COND_STATE_EQUAL\",\"param\":[" + id + ",3,0]}",
                QuestData.QuestAcceptCondition.class);
    }
    @Test
    void scopedToMondstadtOpeningAndThreeActs() {
        for (int id : List.of(351, 361, 363, 353, 354, 360, 311, 370, 20101, 384,
                397, 388, 390, 394, 398, 396))
            assertTrue(QuestData.isReviewedMondstadtMainQuest(id));
        for (int id : List.of(303, 1000, 1301, 7005))
            assertFalse(QuestData.isReviewedMondstadtMainQuest(id));
    }
    @Test
    void restoresBranchInsteadOfSyntheticPhysicalSequence() {
        var excel = List.of(state(2010104));
        var reviewed = List.of(state(2010144));
        var selected = QuestData.selectReviewedMondstadtAcceptConditions(20101, excel, reviewed);
        assertTrue(QuestData.sameAcceptConditions(reviewed, selected));
        assertFalse(QuestData.sameAcceptConditions(excel, selected));
    }
    @Test
    void emptyAndMalformedCompatibilityFallBackToExcel() {
        var excel = List.of(state(39004));
        var malformed = GSON.fromJson(
                "{\"type\":\"QUEST_COND_STATE_EQUAL\",\"param\":[]}",
                QuestData.QuestAcceptCondition.class);
        assertSame(excel, QuestData.selectReviewedMondstadtAcceptConditions(390, excel, List.of(malformed)));
        assertSame(excel, QuestData.selectReviewedMondstadtAcceptConditions(390, excel, List.of()));
    }
}
