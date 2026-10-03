package emu.grasscutter.game.player;

import static emu.grasscutter.scripts.constants.EventType.EVENT_UNLOCK_TRANS_POINT;

import emu.grasscutter.config.RewardOverrides;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.reward.RewardScaler;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.packet.send.PacketScenePointUnlockNotify;

/** Unlocks a statue, waypoint, or dungeon entrance and grants its configured exploration reward. */
public final class TransPointUnlockHelper {
    private TransPointUnlockHelper() {}

    public static boolean unlock(Player player, int sceneId, int pointId, boolean isStatue) {
        return unlock(player, sceneId, pointId, isStatue, false);
    }

    /**
     * Quest 303/352 already carries a separate QUEST_EXEC_UNLOCK_AREA immediately after
     * QUEST_EXEC_UNLOCK_POINT. Keep that transaction split so the client sees point-unlock first and
     * area-unlock second, matching the stock quest data and preserving the native map reveal trigger.
     */
    public static boolean unlockFromQuest(
            Player player, int sceneId, int pointId, boolean isStatue) {
        return unlock(player, sceneId, pointId, isStatue, true);
    }

    private static boolean unlock(
            Player player,
            int sceneId,
            int pointId,
            boolean isStatue,
            boolean deferStatueAreaToQuest) {
        if (player == null) return false;

        var scenePointEntry = GameData.getScenePointEntryById(sceneId, pointId);
        player.getForceLockedScenePoints(sceneId).remove(pointId);

        if (scenePointEntry == null || player.getUnlockedScenePoints(sceneId).contains(pointId)) {
            return false;
        }

        var pointData = scenePointEntry.getPointData();
        if (!isStatue
                && emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(pointData)) {
            isStatue = true;
        }

        player.getUnlockedScenePoints(sceneId).add(pointId);

        var progress = player.getProgressManager();
        if (isStatue
                && !deferStatueAreaToQuest
                && pointData != null
                && pointData.getAreaId() > 0) {
            progress.unlockSceneAreaHierarchy(sceneId, pointData.getAreaId());
        }

        grantReward(player, isStatue);

        player
                .getQuestManager()
                .queueEvent(QuestContent.QUEST_CONTENT_UNLOCK_TRANS_POINT, sceneId, pointId);
        try {
            if (player.getScene() != null) {
                player
                        .getScene()
                        .getScriptManager()
                        .callEvent(new ScriptArgs(0, EVENT_UNLOCK_TRANS_POINT, sceneId, pointId));
            }
        } catch (Throwable ignored) {
        }

        // The point notification belongs to QUEST_EXEC_UNLOCK_POINT. For quest-backed statues do
        // not also synthesize the area/goddess transaction here; QUEST_EXEC_UNLOCK_AREA follows.
        player.sendPacket(new PacketScenePointUnlockNotify(sceneId, pointId));

        if (isStatue && !deferStatueAreaToQuest) {
            progress.refreshStatueTalkGate(sceneId, pointId);
        }

        try {
            int total = 0;
            if (player.getUnlockedScenePoints() != null) {
                for (var points : player.getUnlockedScenePoints().values()) {
                    if (points != null) total += points.size();
                }
            }
            InvestigationHandbookHelper.trigger(
                    player, WatcherTriggerType.TRIGGER_UNLOCK_TRANS_POINT, 0, total);
        } catch (Throwable ignored) {
        }

        player.save();
        return true;
    }

    private static void grantReward(Player player, boolean isStatue) {
        var reward = RewardOverrides.resolveUnlock(isStatue);
        add(player, 201, reward.primogems());
        add(player, RewardScaler.ADVENTURE_EXP_ITEM_ID, reward.adventureExp());
        add(player, 107009, reward.fragileResin());
        add(player, 104003, reward.heroWit());
        add(player, 104013, reward.mysticEnhancementOre());
    }

    private static void add(Player player, int itemId, int baseCount) {
        int count = RewardScaler.scaleCount(itemId, baseCount, 1.0);
        if (count > 0) {
            player.getInventory().addItem(itemId, count, ActionReason.UnlockPointReward);
        }
    }
}
