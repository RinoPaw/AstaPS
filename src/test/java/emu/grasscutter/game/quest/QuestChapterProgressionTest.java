package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.ChapterData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.enums.QuestCond;
import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class QuestChapterProgressionTest {
    @AfterEach
    void clearFixture() {
        GameData.getMainQuestDataMap().clear();
        GameData.getQuestDataMap().clear();
        GameData.getChapterDataMap().clear();
        GameData.getBeginCondQuestMap().clear();
        ChapterData.getBeginQuestChapterMap().clear();
        ChapterData.getEndQuestChapterMap().clear();
    }

    private static void loadChapter363WithExcelPlaceholder() {
        var canonical = JsonUtils.decode("""
                {"subId":36301,"mainId":363,"order":1,
                 "acceptCond":[{"type":"QUEST_COND_STATE_EQUAL","param":[0,3]}],
                 "finishCond":[],"failCond":[],"beginExec":[],"finishExec":[],"failExec":[]}
                """, QuestData.class);
        canonical.onLoad();
        GameData.getQuestDataMap().put(36301, canonical);
        var main = JsonUtils.decode("""
                {"id":363,"series":1001,
                 "subQuests":[{"subId":36301,"order":1,
                   "acceptCond":[{"type":"QUEST_COND_STATE_EQUAL","param":[35202,3,0]}]}]}
                """, MainQuestData.class);
        GameData.getMainQuestDataMap().put(363, main);
        main.onLoad();
        var chapter = JsonUtils.decode(
                "{\"id\":1001,\"beginQuestId\":36301,\"endQuestId\":31101}",
                ChapterData.class);
        GameData.getChapterDataMap().put(1001, chapter);
        chapter.onLoad();
    }

    @Test
    void originalChapterPredecessorReplacesTheSyntheticQuestZeroCondition() {
        loadChapter363WithExcelPlaceholder();
        var canonical = GameData.getQuestDataMap().get(36301);
        assertArrayEquals(new int[]{0,3}, canonical.getAcceptCond().get(0).getParam());
        assertTrue(QuestManager.opensUnlinked(363));

        QuestChapterProgression.reconcileResourceConditions();

        assertArrayEquals(new int[]{35202,3,0}, canonical.getAcceptCond().get(0).getParam());
        assertEquals(QuestData.QuestSource.BIN_OUTPUT,
                canonical.getFieldSource(QuestData.QuestField.ACCEPT_COND));
        assertFalse(QuestManager.opensUnlinked(363));
        assertTrue(GameData.getQuestDataByConditions(
                QuestCond.QUEST_COND_STATE_EQUAL, 35202, "").contains(canonical));
        var obsolete = GameData.getQuestDataByConditions(
                QuestCond.QUEST_COND_STATE_EQUAL, 0, "");
        assertTrue(obsolete == null || !obsolete.contains(canonical));
    }

    @Test
    void reconciliationIsIdempotentAndDoesNotStartAQuest() {
        loadChapter363WithExcelPlaceholder();
        QuestChapterProgression.reconcileResourceConditions();
        QuestChapterProgression.reconcileResourceConditions();
        var list = GameData.getQuestDataByConditions(
                QuestCond.QUEST_COND_STATE_EQUAL, 35202, "");
        assertNotNull(list);
        assertEquals(1, list.stream().filter(q -> q.getId() == 36301).count());
    }

    @Test
    void missingSourceDoesNotInventChapterDependencies() {
        loadChapter363WithExcelPlaceholder();
        GameData.getMainQuestDataMap().clear();
        QuestChapterProgression.reconcileResourceConditions();
        assertArrayEquals(new int[]{0,3},
                GameData.getQuestDataMap().get(36301).getAcceptCond().get(0).getParam());
    }

    @Test
    void nonStateConditionInSourceCannotBypassChapterRules() {
        loadChapter363WithExcelPlaceholder();
        var main = JsonUtils.decode("""
                {"id":363,"subQuests":[{"subId":36301,"order":1,
                 "acceptCond":[{"type":"QUEST_COND_PLAYER_LEVEL_EQUAL_GREATER","param":[1,0,0]}]}]}
                """, MainQuestData.class);
        GameData.getMainQuestDataMap().put(363, main);
        QuestChapterProgression.reconcileResourceConditions();
        assertArrayEquals(new int[]{0,3},
                GameData.getQuestDataMap().get(36301).getAcceptCond().get(0).getParam());
    }
}
