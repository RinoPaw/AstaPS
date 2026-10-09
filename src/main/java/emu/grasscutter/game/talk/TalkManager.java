package emu.grasscutter.game.talk;

import static emu.grasscutter.game.quest.enums.QuestCond.QUEST_COND_COMPLETE_TALK;
import static emu.grasscutter.game.quest.enums.QuestContent.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData.TalkData;
import emu.grasscutter.data.excels.TalkConfigData;
import emu.grasscutter.game.entity.EntityNPC;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.*;
import emu.grasscutter.server.event.player.PlayerNpcTalkEvent;
import lombok.NonNull;

public final class TalkManager extends BasePlayerManager {
    public TalkManager(@NonNull Player player) {
        super(player);
    }

    /**
     * Invoked when a talk is triggered.
     *
     * @param talkId The ID of the talk.
     * @param npcEntityId The entity ID of the NPC being talked to.
     */
    public void triggerTalkAction(int talkId, int npcEntityId) {
        var player = this.getPlayer();

        var talkData = GameData.getTalkConfigDataMap().get(talkId);
        boolean tracePrologue = talkId == 35404 || talkId == 35601 || talkId == 35604 || talkId == 36001;

        // Invoke PlayerNpcTalkEvent.
        var event = new PlayerNpcTalkEvent(player, talkData, talkId, npcEntityId);
        if (!event.call()) {
            if (tracePrologue) {
                Grasscutter.getLogger().warn("[Prologue] Talk {} cancelled by event", talkId);
            }
            return;
        }

        if (talkData != null) {
            // Quest actors can be client-local. If a server entity exists, validate the NPC/model
            // identity rather than the scene-local placement config id.
            var entity = player.getScene().getEntityById(npcEntityId);
            if (!isTalkNpc(talkData, entity)) {
                if (tracePrologue) {
                    Grasscutter.getLogger().warn(
                            "[Prologue] Talk {} rejected: npcEntity={} expectedNpcIds={} actualNpcId={}",
                            talkId, npcEntityId, talkData.getNpcId(),
                            entity != null ? entity.getEntityTypeId() : 0);
                }
                return;
            }

            // Execute the talk action on associated handlers.
            talkData
                    .getFinishExec()
                    .forEach(e -> player.getServer().getTalkSystem().triggerExec(player, talkData, e));

            // Save the talk value to the quest's data.
            this.saveTalkToQuest(talkId, talkData.getQuestId());
        }

        // Invoke the talking events for quests.
        var questManager = player.getQuestManager();
        questManager.queueEvent(QUEST_CONTENT_COMPLETE_ANY_TALK, talkId);
        questManager.queueEvent(QUEST_CONTENT_COMPLETE_TALK, talkId);
        questManager.queueEvent(QUEST_COND_COMPLETE_TALK, talkId);
        if (tracePrologue) {
            Grasscutter.getLogger().info(
                    "[Prologue] Talk {} queued COMPLETE_TALK and COMPLETE_TALK prerequisite for uid={}",
                    talkId, player.getUid());
        }
    }

    static boolean isTalkNpc(TalkConfigData talkData, GameEntity entity) {
        if (entity == null || talkData.getNpcId() == null || talkData.getNpcId().isEmpty()) return true;
        return entity instanceof EntityNPC && talkData.getNpcId().contains(entity.getEntityTypeId());
    }

    public void saveTalkToQuest(int talkId, int mainQuestId) {
        // TODO, problem with this is that some talks for activity also have
        // quest id, which isn't present in QuestExcels
        var mainQuest = this.getPlayer().getQuestManager().getMainQuestById(mainQuestId);
        if (mainQuest == null) return;

        mainQuest.getTalks().put(talkId, new TalkData(talkId, ""));
    }
}
