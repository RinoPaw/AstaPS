package emu.grasscutter.game.player;

import static emu.grasscutter.config.Configuration.GAME;
import static emu.grasscutter.scripts.constants.EventType.EVENT_UNLOCK_TRANS_POINT;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.reward.RewardScaler;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.packet.send.PacketScenePointUnlockNotify;
import java.util.Set;

/** Unlocks a statue, waypoint, or dungeon entrance and grants its configured exploration reward. */
public final class TransPointUnlockHelper {
    private static final Set<Integer> STATUE_GADGET_IDS =
            Set.of(70130009, 70130010, 70130011, 73176017);

    private TransPointUnlockHelper() {}

    /** Returns whether a scene point is a Statue of the Seven. */
    public static boolean isStatuePoint(PointData data) {
        if (data == null) return false;
        if (data.getMaxSpringVolume() > 0) return true;
        if (STATUE_GADGET_IDS.contains(data.getGadgetId())) return true;
        String type = data.getType();
        return type != null && type.contains("KDEHKECBDBO");
    }

    public static boolean unlock(Player player, int sceneId, int pointId, boolean isStatue) {
        var scenePointEntry = GameData.getScenePointEntryById(sceneId, pointId);
        if (scenePointEntry == null) {
            return false;
        }

        boolean wasForceLocked = player.getForceLockedScenePoints(sceneId).remove(pointId);
        boolean newlyUnlocked = player.getUnlockedScenePoints(sceneId).add(pointId);
        if (!wasForceLocked && !newlyUnlocked) {
            return false;
        }

        // Both permanent unlock state and quest-forced lock state are authoritative and persisted.
        player.save();

        if (newlyUnlocked) {
            player
                    .getQuestManager()
                    .queueEvent(QuestContent.QUEST_CONTENT_UNLOCK_TRANS_POINT, sceneId, pointId);
            player
                    .getScene()
                    .getScriptManager()
                    .callEvent(new ScriptArgs(0, EVENT_UNLOCK_TRANS_POINT, sceneId, pointId));

            int total =
                    player.getUnlockedScenePoints().values().stream()
                            .mapToInt(java.util.Collection::size)
                            .sum();
            InvestigationHandbookHelper.trigger(
                    player, WatcherTriggerType.TRIGGER_UNLOCK_TRANS_POINT, 0, total);

            try {
                grantReward(player, isStatue);
            } catch (RuntimeException | LinkageError e) {
                // Rewards are auxiliary. The persisted point unlock remains authoritative.
                Grasscutter.getLogger()
                        .error(
                                "Failed to grant unlock reward for uid={} scene={} point={}; "
                                        + "the trans point remains unlocked.",
                                player.getUid(),
                                sceneId,
                                pointId,
                                e);
            }
        }

        player.sendPacket(new PacketScenePointUnlockNotify(sceneId, pointId));
        return true;
    }

    private static void grantReward(Player player, boolean isStatue) {
        var reward = GAME.rewards.unlock(isStatue);

        // Resolve every scaled amount before mutating inventory. If scaling itself fails, no partial
        // reward set is applied and the already-persisted unlock still succeeds.
        int[][] rewards = {
            {201, RewardScaler.scaleCount(201, reward.primogems, 1.0)},
            {
                RewardScaler.ADVENTURE_EXP_ITEM_ID,
                RewardScaler.scaleCount(
                        RewardScaler.ADVENTURE_EXP_ITEM_ID, reward.adventureExp, 1.0)
            },
            {107009, RewardScaler.scaleCount(107009, reward.fragileResin, 1.0)},
            {104003, RewardScaler.scaleCount(104003, reward.heroWit, 1.0)},
            {104013, RewardScaler.scaleCount(104013, reward.mysticEnhancementOre, 1.0)}
        };

        for (int[] entry : rewards) {
            if (entry[1] > 0) {
                player.getInventory().addItem(entry[0], entry[1], ActionReason.UnlockPointReward);
            }
        }
    }
}
