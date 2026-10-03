package emu.grasscutter.game.player;

import static emu.grasscutter.scripts.constants.EventType.EVENT_UNLOCK_TRANS_POINT;

import emu.grasscutter.config.RewardOverrides;
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
        if (scenePointEntry == null || player.getUnlockedScenePoints(sceneId).contains(pointId)) {
            return false;
        }

        player.getForceLockedScenePoints(sceneId).remove(pointId);
        player.getUnlockedScenePoints(sceneId).add(pointId);

        grantReward(player, isStatue);

        player
                .getQuestManager()
                .queueEvent(QuestContent.QUEST_CONTENT_UNLOCK_TRANS_POINT, sceneId, pointId);
        player
                .getScene()
                .getScriptManager()
                .callEvent(new ScriptArgs(0, EVENT_UNLOCK_TRANS_POINT, sceneId, pointId));

        player.sendPacket(new PacketScenePointUnlockNotify(sceneId, pointId));

        int total =
                player.getUnlockedScenePoints().values().stream()
                        .mapToInt(java.util.Collection::size)
                        .sum();
        InvestigationHandbookHelper.trigger(
                player, WatcherTriggerType.TRIGGER_UNLOCK_TRANS_POINT, 0, total);

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
