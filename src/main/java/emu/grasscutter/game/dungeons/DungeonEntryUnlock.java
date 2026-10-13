package emu.grasscutter.game.dungeons;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.dungeon.DungeonEntryData;
import emu.grasscutter.game.dungeons.enums.DungeonEntryCondCombType;
import emu.grasscutter.game.dungeons.enums.DungeonEntrySatisfiedConditionType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.enums.QuestState;
import java.util.function.IntPredicate;

/** Distinguishes dungeon entrance eligibility, exploration, and first-unlock rewards. */
public final class DungeonEntryUnlock {
    private DungeonEntryUnlock() {}

    public static DungeonEntryData find(int sceneId, int pointId) {
        for (var entry : GameData.getDungeonEntryDataMap().values()) {
            if (entry.getSceneId() == sceneId && entry.getDungeonEntryId() == pointId) {
                return entry;
            }
        }
        return null;
    }

    /** An entry with no rewardDataId has no first-exploration grant. */
    public static int rewardId(int sceneId, int pointId) {
        var entry = find(sceneId, pointId);
        return entry == null ? 0 : entry.getRewardDataId();
    }

    /** Only the actual stored unlock state can enable handbook quick-open UI. */
    public static boolean isUnlocked(Player player, int sceneId, int pointId) {
        return player != null
                && player.getUnlockedScenePoints(sceneId).contains(pointId)
                && !player.isScenePointForceLocked(sceneId, pointId);
    }

    public static boolean canExplore(Player player, DungeonEntryData entry) {
        if (player == null || entry == null) return false;
        if (isUnlocked(player, entry.getSceneId(), entry.getDungeonEntryId())) return true;
        return conditionsMet(
                entry,
                player.getLevel(),
                questId -> {
                    var manager = player.getQuestManager();
                    var quest = manager == null ? null : manager.getQuestById(questId);
                    return quest != null && quest.getState() == QuestState.QUEST_STATE_FINISHED;
                });
    }

    /** Resource conditions are OR/AND gates; missing conditions do not grant automatic unlocks. */
    public static boolean conditionsMet(
            DungeonEntryData entry, int level, IntPredicate questFinished) {
        if (entry == null) return false;
        if (entry.isDefaultOpen()) return true;
        var conditions = entry.getSatisfiedCond();
        if (conditions == null || conditions.isEmpty()) return true;

        boolean hasCondition = false;
        boolean result = entry.getCondComb() == DungeonEntryCondCombType.DUNGEON_ENTRY_COND_COMB_LOGIC_AND;
        for (var condition : conditions) {
            if (condition == null || condition.getType() == null
                    || condition.getType() == DungeonEntrySatisfiedConditionType.DUNGEON_ENTRY_CONDITION_NONE) {
                continue;
            }
            hasCondition = true;
            boolean met = switch (condition.getType()) {
                case DUNGEON_ENTRY_CONDITION_LEVEL -> level >= condition.getParam1();
                case DUNGEON_ENTRY_CONDITION_QUEST -> questFinished.test(condition.getParam1());
                default -> false;
            };
            if (entry.getCondComb() == DungeonEntryCondCombType.DUNGEON_ENTRY_COND_COMB_LOGIC_AND) {
                result &= met;
            } else {
                result |= met;
            }
        }
        return !hasCondition || result;
    }
}
