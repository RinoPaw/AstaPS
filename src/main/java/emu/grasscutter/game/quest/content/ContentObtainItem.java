package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_OBTAIN_ITEM;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import lombok.val;

@QuestValueContent(QUEST_CONTENT_OBTAIN_ITEM)
public class ContentObtainItem extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        int[] target = condition.getParam();
        if (params.length == 0 || target == null || target.length == 0 || params[0] != target[0]) {
            return false;
        }
        int itemId = target[0];
        var targetAmount = condition.getCount();
        if (targetAmount == 0) {
            targetAmount = 1;
        }
        val amount = quest.getOwner().getInventory().getItemCountById(itemId);
        return amount >= targetAmount;
    }
}
