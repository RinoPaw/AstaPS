package emu.grasscutter.game.player;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.managers.StatueTalkQuests;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.server.packet.send.PacketQuestListUpdateNotify;

/**
 * Test-branch bridge that exposes the client statue interaction without unlocking the statue on
 * proximity. Generic statues still complete through their native 303xx talk quest.
 */
public final class StatueActivationProbe {
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
        int questId = StatueTalkQuests.questForArea(areaId);
        QuestState serverState = null;

        if (questId > 0) {
            var quest = player.getQuestManager().getQuestById(questId);
            if (quest == null) {
                player.getQuestManager().addQuest(questId);
                quest = player.getQuestManager().getQuestById(questId);
            }

            // Older private-server login code marks every 303xx child FINISHED solely to expose
            // the client F/talk surface. That destroys the native activation transaction because
            // COMPLETE_TALK only finishes UNFINISHED children. Repair the server state here while
            // keeping the client-facing gate FINISHED below.
            if (quest != null && quest.getState() == QuestState.QUEST_STATE_FINISHED) {
                quest.setState(QuestState.QUEST_STATE_UNFINISHED);
                quest.setFinishTime(0);
                quest.save();
            }
            if (quest != null) serverState = quest.getState();

            // Client-only gate: keep the goddess/F interaction visible while the real server quest
            // remains UNFINISHED and can consume NpcTalkReq(303xx).
            player.sendPacket(new PacketQuestListUpdateNotify(questId, 303, 3));
        }

        // Loading the goddess suite/worktop is presentation only. Do not unlock point or area here.
        player.getProgressManager().refreshStatueGoddessNpc(sceneId, pointId);

        Grasscutter.getLogger()
                .info(
                        "[statue-probe] prepared locked statue interaction uid={} scene={} point={} area={} quest={} serverState={}",
                        player.getUid(),
                        sceneId,
                        pointId,
                        areaId,
                        questId,
                        serverState);
        return true;
    }
}
