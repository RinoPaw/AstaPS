package emu.grasscutter.game.player;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.managers.StatueTalkQuests;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.server.packet.send.PacketQuestListUpdateNotify;

/**
 * Test-branch bridge that restores stock pre-activation quest state without unlocking statues on
 * proximity. Generic statues use quest 303xx; the starter Mondstadt statue uses quest 35205.
 */
public final class StatueActivationProbe {
    private static final int STARTER_SCENE = 3;
    private static final int STARTER_POINT = 7;
    private static final int STARTER_QUEST = 35205;

    private StatueActivationProbe() {}

    /**
     * Undo the legacy login path which marks statue activation quests FINISHED even while their
     * points remain locked. Run after login seeding/cleanup so server and client both see the real
     * pre-activation state before the player starts interacting with statues.
     */
    public static int restoreLockedActivationQuests(Player player, boolean sendToClient) {
        if (player == null) return 0;

        int restored = 0;
        var pointIds = GameData.getScenePointsPerScene().get(STARTER_SCENE);
        if (pointIds == null) return 0;

        for (int pointId : pointIds) {
            var entry = GameData.getScenePointEntryById(STARTER_SCENE, pointId);
            if (entry == null || entry.getPointData() == null) continue;
            var pointData = entry.getPointData();
            if (!StatueTalkQuests.isStatuePoint(pointData)) continue;

            boolean locked =
                    player.isScenePointForceLocked(STARTER_SCENE, pointId)
                            || !player.getUnlockedScenePoints(STARTER_SCENE).contains(pointId);
            if (!locked) continue;

            int questId = activationQuestId(STARTER_SCENE, pointId, pointData.getAreaId());
            if (questId <= 0) continue;

            var quest = player.getQuestManager().getQuestById(questId);
            if (quest == null) continue;

            if (reopen(quest)) {
                restored++;
            }
            if (sendToClient && quest.getState() == QuestState.QUEST_STATE_UNFINISHED) {
                player.sendPacket(new PacketQuestListUpdateNotify(quest));
            }
        }

        Grasscutter.getLogger()
                .info(
                        "[statue-probe] restored locked activation quests uid={} count={} sent={}",
                        player.getUid(),
                        restored,
                        sendToClient);
        return restored;
    }

    public static boolean prepare(Player player, int sceneId, int pointId) {
        if (player == null || sceneId <= 0 || pointId <= 0) return false;

        var entry = GameData.getScenePointEntryById(sceneId, pointId);
        if (entry == null || entry.getPointData() == null) return false;
        var pointData = entry.getPointData();
        if (!StatueTalkQuests.isStatuePoint(pointData)) return false;

        boolean unlocked =
                player.getUnlockedScenePoints(sceneId).contains(pointId)
                        && !player.isScenePointForceLocked(sceneId, pointId);
        if (unlocked) {
            player.getProgressManager().refreshStatueTalkGate(sceneId, pointId);
            return true;
        }

        int areaId = pointData.getAreaId();
        int questId = activationQuestId(sceneId, pointId, areaId);
        int parentQuestId =
                sceneId == STARTER_SCENE && pointId == STARTER_POINT ? 352 : 303;
        QuestState serverState = null;
        boolean sentToClient = false;

        if (questId > 0) {
            var quest = player.getQuestManager().getQuestById(questId);
            if (quest == null) {
                player.getQuestManager().addQuest(questId);
                quest = player.getQuestManager().getQuestById(questId);
            }

            if (quest != null) {
                reopen(quest);
                serverState = quest.getState();
                player.sendPacket(new PacketQuestListUpdateNotify(quest));
                sentToClient = true;
            }
        }

        // Do not load the post-unlock goddess suite/worktop while the statue is still locked. The
        // activation surface belongs to the scene point plus its unfinished activation quest.
        Grasscutter.getLogger()
                .info(
                        "[statue-probe] activation-ready uid={} scene={} point={} area={} quest={}/{} state={} sent={}",
                        player.getUid(),
                        sceneId,
                        pointId,
                        areaId,
                        parentQuestId,
                        questId,
                        serverState,
                        sentToClient);
        return true;
    }

    private static int activationQuestId(int sceneId, int pointId, int areaId) {
        if (sceneId == STARTER_SCENE && pointId == STARTER_POINT) {
            return STARTER_QUEST;
        }
        return StatueTalkQuests.questForArea(areaId);
    }

    private static boolean reopen(emu.grasscutter.game.quest.GameQuest quest) {
        if (quest == null || quest.getState() != QuestState.QUEST_STATE_FINISHED) {
            return false;
        }

        quest.setState(QuestState.QUEST_STATE_UNFINISHED);
        quest.setFinishTime(0);
        if (quest.getFinishProgressList() != null) {
            for (int i = 0; i < quest.getFinishProgressList().length; i++) {
                quest.setFinishProgress(i, 0);
            }
        }
        quest.save();
        return true;
    }
}
