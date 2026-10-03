package emu.grasscutter.game.talk;

import static emu.grasscutter.game.quest.enums.QuestCond.QUEST_COND_COMPLETE_TALK;
import static emu.grasscutter.game.quest.enums.QuestContent.*;

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

        // Invoke PlayerNpcTalkEvent.
        var event = new PlayerNpcTalkEvent(player, talkData, talkId, npcEntityId);
        if (!event.call()) return;

        if (talkData != null) {
            // Check if the NPC id is valid.
            var entity = player.getScene().getEntityById(npcEntityId);
            boolean accepted = isTalkNpc(talkData, entity);
            if (talkId == 35216) {
                emu.grasscutter.Grasscutter.getLogger()
                        .info(
                                "[quest352] talk uid={} talk={} npcEntity={} npcId={} configId={} accepted={}",
                                player.getUid(), talkId, npcEntityId,
                                entity != null ? entity.getEntityTypeId() : null,
                                entity != null ? entity.getConfigId() : null, accepted);
            }
            if (!accepted) return;

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
    }

    static boolean isTalkNpc(TalkConfigData talkData, GameEntity entity) {
        // Quest actors can be client-local, with no matching server entity. When an entity exists,
        // compare the NPC/model id; configId identifies its placement inside a scene group.
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
