package emu.grasscutter.data.excels.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.util.List;
import org.junit.jupiter.api.Test;

final class Quest351NativePrerequisiteTest {
    private static final Gson GSON = new Gson();

    private static QuestData.QuestAcceptCondition cond(String type, int id, int state) {
        String json = "{\"type\":\"" + type
                + "\",\"param\":[" + id + "," + state + ",0],\"param_str\":\"\"}";
        return GSON.fromJson(json, QuestData.QuestAcceptCondition.class);
    }

    @Test
    void paimonHandoffUsesNative35100FinishedPrerequisite() {
        var stale = List.of(cond("QUEST_COND_STATE_EQUAL", 0, 3));
        var nativeValue = List.of(cond("QUEST_COND_STATE_EQUAL", 35100, 3));
        var chosen = QuestData.selectReviewedPrologueAcceptConditions(351, 35101, stale, nativeValue);
        assertEquals(35100, chosen.get(0).getParam()[0]);
        assertFalse(QuestData.sameAcceptConditions(stale, chosen));
        assertTrue(QuestData.sameAcceptConditions(nativeValue, chosen));
    }

    @Test
    void otherMainQuestsAndMissingNativeConditionsRemainUntouched() {
        var excel = List.of(cond("QUEST_COND_STATE_EQUAL", 35100, 3));
        var nativeValue = List.of(cond("QUEST_COND_STATE_EQUAL", 35205, 3));
        assertSame(excel, QuestData.selectReviewedPrologueAcceptConditions(353, 35302, excel, nativeValue));
        assertSame(excel, QuestData.selectReviewedPrologueAcceptConditions(351, 35101, excel, null));
        assertSame(excel, QuestData.selectReviewedPrologueAcceptConditions(351, 35101, excel, List.of()));
    }

    @Test
    void amberHandoffUses35601InsteadOfSyntheticPrevious35602() {
        var flattened = List.of(cond("QUEST_COND_STATE_EQUAL", 35602, 3));
        var reviewed = List.of(cond("QUEST_COND_STATE_EQUAL", 35601, 3));
        var chosen = QuestData.selectReviewedPrologueAcceptConditions(356, 35603, flattened, reviewed);
        assertEquals(35601, chosen.get(0).getParam()[0]);
        assertTrue(QuestData.sameAcceptConditions(reviewed, chosen));
        assertSame(flattened,
                QuestData.selectReviewedPrologueAcceptConditions(356, 35602, flattened, reviewed));
    }

    @Test
    void native351MultipleConditionsRetainOrderAndSemantics() {
        var nativeValue = List.of(
                cond("QUEST_COND_STATE_EQUAL", 35106, 3),
                cond("QUEST_COND_STATE_NOT_EQUAL", 35105, 3));
        var chosen = QuestData.selectReviewedPrologueAcceptConditions(351, 35103, List.of(), nativeValue);
        assertEquals(2, chosen.size());
        assertEquals("QUEST_COND_STATE_NOT_EQUAL", chosen.get(1).getType().name());
    }
}
