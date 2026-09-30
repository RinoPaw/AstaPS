package emu.grasscutter.game.player;

import static emu.grasscutter.scripts.constants.EventType.EVENT_UNLOCK_TRANS_POINT;

import emu.grasscutter.config.RewardOverrides;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.packet.send.PacketScenePointUnlockNotify;

/** Unlocks a statue, waypoint, or dungeon entrance and grants its configured exploration reward. */
public final class TransPointUnlockHelper {
    private TransPointUnlockHelper() {}

    public static boolean unlock(Player player, int sceneId, int pointId, boolean isStatue) {
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
        if (isStatue && pointData != null && pointData.getAreaId() > 0) {
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

        if (isStatue) {
            progress.refreshStatueTalkGate(sceneId, pointId);
        }

        player.sendPacket(new PacketScenePointUnlockNotify(sceneId, pointId));
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
        add(player, 102, reward.adventureExp());
        add(player, 107009, reward.fragileResin());
        add(player, 104003, reward.heroWit());
        add(player, 104013, reward.mysticEnhancementOre());
    }

    private static void add(Player player, int itemId, int count) {
        if (count > 0) {
            player.getInventory().addItem(itemId, count, ActionReason.UnlockPointReward);
        }
    }
}
