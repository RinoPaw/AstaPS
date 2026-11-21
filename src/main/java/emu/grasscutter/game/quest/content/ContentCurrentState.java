package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.*;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.world.Position;

import java.util.regex.Pattern;

@QuestValueContent(
        value = QUEST_CONTENT_QUEST_GLOBAL_VAR_EQUAL,
        aliases = {
            QUEST_CONTENT_QUEST_GLOBAL_VAR_NOT_EQUAL, QUEST_CONTENT_QUEST_GLOBAL_VAR_GREATER,
            QUEST_CONTENT_QUEST_GLOBAL_VAR_LESS, QUEST_CONTENT_ITEM_NUM_EQUAL,
            QUEST_CONTENT_ITEM_NUM_GREATER, QUEST_CONTENT_ITEM_NUM_LESS,
            QUEST_CONTENT_PRESENT_AT_SPECIFIC_SCENE, QUEST_CONTENT_LEAVE_SCENE_RANGE,
            QUEST_CONTENT_PARENT_QUEST_STATE_EQUAL
        })
public final class ContentCurrentState extends BaseContent {
    private static final Pattern POINT =
            Pattern.compile("\\[(\\d+)_[^:]+:([^,]+),([^,]+),([^,\\]]+)(?:,[^\\]]*)?\\]");

    @Override
    public boolean execute(
            GameQuest quest,
            QuestData.QuestContentCondition condition,
            String paramStr,
            int... params) {
        var expected = condition.getParam();
        var type = condition.getType();
        int required = type == QUEST_CONTENT_PRESENT_AT_SPECIFIC_SCENE ? 1 : 2;
        if (expected == null || expected.length < required) return false;
        if (params.length > 0 && params[0] != expected[0]) return false;
        var player = quest.getOwner();
        // 事件只负责唤醒检查，数值以当前存档为准。
        return switch (type) {
            case QUEST_CONTENT_QUEST_GLOBAL_VAR_EQUAL,
                    QUEST_CONTENT_QUEST_GLOBAL_VAR_NOT_EQUAL,
                    QUEST_CONTENT_QUEST_GLOBAL_VAR_GREATER,
                    QUEST_CONTENT_QUEST_GLOBAL_VAR_LESS ->
                    compare(
                            type,
                            player.getQuestManager().getQuestGlobalVarValue(expected[0]),
                            expected[1]);
            case QUEST_CONTENT_ITEM_NUM_EQUAL,
                    QUEST_CONTENT_ITEM_NUM_GREATER,
                    QUEST_CONTENT_ITEM_NUM_LESS ->
                    compare(type, player.getInventory().getItemCountById(expected[0]), expected[1]);
            case QUEST_CONTENT_PRESENT_AT_SPECIFIC_SCENE ->
                    player.getScene() != null && player.getScene().getId() == expected[0];
            case QUEST_CONTENT_PARENT_QUEST_STATE_EQUAL -> {
                var parent = player.getQuestManager().getMainQuestById(expected[0]);
                yield parent != null && parent.getState().getValue() == expected[1];
            }
            case QUEST_CONTENT_LEAVE_SCENE_RANGE -> {
                var scene = player.getScene();
                var center = parsePoint(condition.getParamStr(), expected[0]);
                yield scene != null
                        && scene.getId() == expected[0]
                        && expected[1] >= 0
                        && center != null
                        && player.getPosition().computeDistance(center) > expected[1];
            }
            default -> false;
        };
    }

    static boolean compare(QuestContent type, int actual, int expected) {
        return switch (type) {
            case QUEST_CONTENT_QUEST_GLOBAL_VAR_EQUAL, QUEST_CONTENT_ITEM_NUM_EQUAL ->
                    actual == expected;
            case QUEST_CONTENT_QUEST_GLOBAL_VAR_NOT_EQUAL -> actual != expected;
            case QUEST_CONTENT_QUEST_GLOBAL_VAR_GREATER, QUEST_CONTENT_ITEM_NUM_GREATER ->
                    actual > expected;
            case QUEST_CONTENT_QUEST_GLOBAL_VAR_LESS, QUEST_CONTENT_ITEM_NUM_LESS ->
                    actual < expected;
            default -> false;
        };
    }

    public static boolean checkOnStart(QuestContent type) {
        return switch (type) {
            case QUEST_CONTENT_QUEST_GLOBAL_VAR_EQUAL,
                    QUEST_CONTENT_QUEST_GLOBAL_VAR_NOT_EQUAL,
                    QUEST_CONTENT_QUEST_GLOBAL_VAR_GREATER,
                    QUEST_CONTENT_QUEST_GLOBAL_VAR_LESS,
                    QUEST_CONTENT_ITEM_NUM_EQUAL,
                    QUEST_CONTENT_ITEM_NUM_GREATER,
                    QUEST_CONTENT_ITEM_NUM_LESS,
                    QUEST_CONTENT_PRESENT_AT_SPECIFIC_SCENE,
                    QUEST_CONTENT_PARENT_QUEST_STATE_EQUAL ->
                    true;
            default -> false;
        };
    }

    static Position parsePoint(String spec, int sceneId) {
        if (spec == null) return null;
        var match = POINT.matcher(spec.trim());
        if (!match.matches()) return null;
        try {
            if (Integer.parseInt(match.group(1)) != sceneId) return null;
            float x = Float.parseFloat(match.group(2));
            float y = Float.parseFloat(match.group(3));
            float z = Float.parseFloat(match.group(4));
            return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)
                    ? new Position(x, y, z)
                    : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
