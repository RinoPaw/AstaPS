package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_UNLOCK_AREA;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

@QuestValueContent(QUEST_CONTENT_UNLOCK_AREA)
public class ContentUnlockArea extends BaseContent {
    /**
     * Unlocks are durable: a multi-area quest must retain progress for areas
     * unlocked earlier. Re-evaluate the exact scene/area pair from player state
     * instead of accepting any event with just one matching argument.
     */
    static boolean isUnlocked(
            java.util.Map<Integer, java.util.Set<Integer>> unlocked, int sceneId, int areaId) {
        if (unlocked == null || sceneId <= 0 || areaId <= 0) return false;
        var areas = unlocked.get(sceneId);
        return areas != null && areas.contains(areaId);
    }

    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        if (quest == null || condition == null || condition.getParam() == null
                || condition.getParam().length < 2) return false;
        return isUnlocked(
                quest.getOwner().getUnlockedSceneAreas(),
                condition.getParam()[0], condition.getParam()[1]);
    }
}
