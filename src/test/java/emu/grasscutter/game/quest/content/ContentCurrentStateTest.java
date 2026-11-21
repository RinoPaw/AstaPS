package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.*;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.*;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import org.junit.jupiter.api.Test;

final class ContentCurrentStateTest {
    @Test
    void registersEveryStateAliasWithItsClientEnumValue() {
        var handlers = new Int2ObjectOpenHashMap<BaseContent>();
        new QuestSystem(null).registerHandler(handlers, ContentCurrentState.class);
        for (int id : new int[] {150, 160, 161, 162, 168, 169, 170, 172, 177, 186}) {
            assertInstanceOf(ContentCurrentState.class, handlers.get(id));
        }
    }

    @Test
    void rechecksLiveGlobalValueAndRejectsAnotherVariablesEvent() {
        var player = new Player();
        var manager = new QuestManager(player);
        Player owner =
                new Player() {
                    @Override
                    public QuestManager getQuestManager() {
                        return manager;
                    }
                };
        var data =
                new Gson()
                        .fromJson(
                                "{\"subId\":7710407,\"mainId\":77104,\"finishCond\":[],\"failCond\":[]}",
                                QuestData.class);
        var quest =
                new GameQuest(null, data) {
                    @Override
                    public Player getOwner() {
                        return owner;
                    }
                };
        var condition = new QuestData.QuestContentCondition();
        condition.setType(QUEST_CONTENT_QUEST_GLOBAL_VAR_EQUAL);
        condition.setParam(new int[] {72, 2});
        var handler = new ContentCurrentState();
        player.getQuestGlobalVariables().put(72, 2);
        assertTrue(handler.execute(quest, condition, "", 72, 999));
        assertFalse(handler.execute(quest, condition, "", 73, 2));
        player.getQuestGlobalVariables().put(72, 1);
        assertFalse(handler.execute(quest, condition, "", 72, 2));
        assertFalse(ContentCurrentState.compare(QUEST_CONTENT_ITEM_NUM_GREATER, 2, 2));
        assertTrue(ContentCurrentState.compare(QUEST_CONTENT_ITEM_NUM_GREATER, 3, 2));
    }

    @Test
    void rangeUsesTheConditionPointIncludingOptionalRotation() {
        // 7710244 的条件中心与导航中心不同。
        var point =
                ContentCurrentState.parsePoint(
                        "[3_14622:8769.230,87.200,8503.074,0.000,299.816,0.000]", 3);
        assertNotNull(point);
        assertEquals(8769.230f, point.getX(), 0.001f);
        assertNull(ContentCurrentState.parsePoint("[4_14622:1,2,3]", 3));
        assertNull(ContentCurrentState.parsePoint("[3_14622:NaN,2,3]", 3));
        assertNull(ContentCurrentState.parsePoint("[3_14622:1,2]", 3));
    }

    @Test
    void malformedAndUnrelatedConditionsDoNotReadPlayerState() {
        var condition = new QuestData.QuestContentCondition();
        condition.setType(QUEST_CONTENT_ITEM_NUM_EQUAL);
        condition.setParam(new int[] {2030, 1});
        var handler = new ContentCurrentState();
        assertFalse(handler.execute(null, condition, "", 2031));
        condition.setParam(new int[0]);
        assertFalse(handler.execute(null, condition, ""));
    }
}
