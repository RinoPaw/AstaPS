package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.ScriptArgs;
import lombok.val;

@QuestValueExec(QuestExec.QUEST_EXEC_NOTIFY_GROUP_LUA)
public class ExecNotifyGroupLua extends QuestExecHandler {

    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        val sceneId = Integer.parseInt(paramStr[0]);
        val groupId = Integer.parseInt(paramStr[1]);

        val scene = quest.getOwner().getScene();
        val scriptManager = scene.getScriptManager();

        if (scene.getId() != sceneId) {
            return false;
        }
        // Capture the action's meaning now: a deferred beginExec must never turn into
        // EVENT_QUEST_FINISH just because the quest state changed before scene loading.
        final var startingState = quest.getState();
        final var eventType =
                startingState == QuestState.QUEST_STATE_FINISHED
                        ? EventType.EVENT_QUEST_FINISH
                        : EventType.EVENT_QUEST_START;
        scene.runWhenFinished(
                () -> {
                    if (quest.getOwner().getScene() != scene || quest.getState() != startingState
                            || scriptManager.isDestroyed()) {
                        emu.grasscutter.Grasscutter.getLogger()
                                .debug("Ignoring stale Lua group notification main={} sub={} group={}",
                                        quest.getMainQuestId(), quest.getSubQuestId(), groupId);
                        return;
                    }
                    // A quest may reference a scene group that is not yet in the player's
                    // visible grid. Register that group's native Lua triggers before
                    // dispatching its quest event; otherwise callEvent silently finds none.
                    if (scriptManager.getGroupById(groupId) == null) {
                        emu.grasscutter.Grasscutter.getLogger()
                                .warn("Quest {} could not notify Lua group {} in scene {}: group unavailable",
                                        quest.getSubQuestId(), groupId, sceneId);
                        return;
                    }
                    scriptManager.callEvent(
                            new ScriptArgs(
                                            groupId,
                                            eventType,
                                            quest.getSubQuestId(),
                                            startingState == QuestState.QUEST_STATE_FINISHED ? 1 : 0)
                                    .setEventSource(quest.getSubQuestId()));
                });

        return true;
    }
}
