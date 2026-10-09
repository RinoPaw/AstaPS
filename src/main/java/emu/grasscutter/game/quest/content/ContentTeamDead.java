package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_TEAM_DEAD;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

@QuestValueContent(QUEST_CONTENT_TEAM_DEAD)
public class ContentTeamDead extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        // This content is only queued from TeamManager after it has confirmed that no living
        // replacement avatar remains in the active team.
        return true;
    }
}
