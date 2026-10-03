package emu.grasscutter.game.player;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.managers.StatueTalkQuests;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.server.packet.send.PacketQuestListUpdateNotify;

/**
 * Test-branch bridge that restores the stock pre-activation quest state without unlocking a statue
 * on proximity. Generic statues use quest 303xx; the starter Mondstadt statue uses quest 35205.
 */
public final class StatueActivationProbe {
    private static final int STARTER_SCENE = 3;
    private static final int STARTER_POINT = 7;
    private static final int STARTER_QUEST = 35205;

    private StatueActivationProbe() {}

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
        int questId;
        int parentQuestId;
        if (sceneId == STARTER_SCENE && pointId == STARTER_POINT) {
            questId = STARTER_QUEST;
            parentQuestId = 352;
        } else {
            questId = StatueTalkQuests.questForArea(areaId);
            parentQuestId = 303;
        }

        QuestState serverState = null;
        boolean sentToClient = false;
        if (questId > 0) {
            var quest = player.getQuestManager().getQuestById(questId);
            if (quest == null) {
                player.getQuestManager().addQuest(questId);
                quest = player.getQuestManager().getQuestById(questId);
            }

            // Legacy private-server login code pre-finishes statue gate quests. Restore the real
            // server transaction state so the client can drive the quest's native finish condition.
            if (quest != null && quest.getState() == QuestState.QUEST_STATE_FINISHED) {
                quest.setState(QuestState.QUEST_STATE_UNFINISHED);
                quest.setFinishTime(0);
                if (quest.getFinishProgressList() != null) {
                    for (int i = 0; i < quest.getFinishProgressList().length; i++) {
                        quest.setFinishProgress(i, 0);
                    }
                }
                quest.save();
            }

            if (quest != null) {
                serverState = quest.getState();
                // PacketQuestListUpdateNotify deliberately lets 303/352 through even when general
                // questing is disabled. This packet must contain the real UNFINISHED quest; an empty
                // update was the reason the locked-statue interaction never appeared in earlier probes.
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
}
