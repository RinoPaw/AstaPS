package emu.grasscutter.game.player;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.managers.StatueTalkQuests;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.server.packet.send.PacketQuestListUpdateNotify;

/**
 * Test-branch bridge that exposes the client statue activation state without unlocking the statue
 * on proximity. Generic statues still complete through their native 303xx talk quest.
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

            // Private-server login code used to pre-finish every 303xx child. Native activation
            // requires the matching child to stay UNFINISHED until COMPLETE_TALK(303xx) arrives.
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
                // Keep client and server on the same pre-activation state. A FINISHED client-only
                // quest describes the post-activation goddess interaction and suppresses the locked
                // statue activation surface on the 7.1 client.
                player.sendPacket(new PacketQuestListUpdateNotify(quest));
            }
        }

        // Do not load the post-unlock goddess suite/worktop while the statue is still locked. The
        // activation interaction belongs to the scene point + unfinished 303xx quest itself.
        Grasscutter.getLogger()
                .info(
                        "[statue-probe] prepared locked statue activation uid={} scene={} point={} area={} quest={} state={} clientState=UNFINISHED",
                        player.getUid(),
                        sceneId,
                        pointId,
                        areaId,
                        questId,
                        serverState);
        return true;
    }
}
