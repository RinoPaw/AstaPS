package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import lombok.val;

@QuestValueExec(QuestExec.QUEST_EXEC_REFRESH_GROUP_SUITE)
public class ExecRefreshGroupSuite extends QuestExecHandler {
    private static final int MAX_SCRIPT_INIT_RETRIES = 100;

    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        return executeWhenReady(quest, paramStr, 0);
    }

    private boolean executeWhenReady(GameQuest quest, String[] paramStr, int attempt) {
        if (paramStr.length < 2) {
            Grasscutter.getLogger().warn(
                    "Quest {} refresh-group-suite exec has invalid params {}",
                    quest.getSubQuestId(),
                    java.util.Arrays.toString(paramStr));
            return false;
        }

        val sceneId = Integer.parseInt(paramStr[0]);
        val scene = quest.getOwner().getWorld().getSceneById(sceneId);
        if (scene == null) {
            Grasscutter.getLogger().warn(
                    "Quest {} could not refresh group suite: scene {} is unavailable",
                    quest.getSubQuestId(),
                    sceneId);
            return false;
        }

        val scriptManager = scene.getScriptManager();
        if (!scriptManager.isInit()) {
            if (!scriptManager.isInitAttempted() && attempt < MAX_SCRIPT_INIT_RETRIES) {
                if (attempt == 0) {
                    Grasscutter.getLogger().debug(
                            "Quest {} deferring group-suite refresh in scene {} until scripts initialize",
                            quest.getSubQuestId(),
                            sceneId);
                }
                scene.getScheduler()
                        .scheduleDelayedTaskTicks(
                                () -> executeWhenReady(quest, paramStr, attempt + 1), 1);
                return true;
            }

            Grasscutter.getLogger().warn(
                    "Quest {} could not refresh group suite in scene {}: scripts failed to initialize after {} attempt(s)",
                    quest.getSubQuestId(),
                    sceneId,
                    attempt);
            return false;
        }

        val entries = paramStr[1].split(";");
        boolean result = true;
        for (var entry : entries) {
            val entryArray = entry.split(",");
            if (entryArray.length < 2) {
                Grasscutter.getLogger().warn(
                        "Quest {} refresh-group-suite exec has invalid entry {}",
                        quest.getSubQuestId(),
                        entry);
                result = false;
                continue;
            }

            val groupId = Integer.parseInt(entryArray[0]);
            val suiteId = Integer.parseInt(entryArray[1]);
            val group = scriptManager.getGroupById(groupId);
            if (group == null) {
                Grasscutter.getLogger().warn(
                        "Quest {} could not refresh group {} suite {} in scene {}: group is unavailable",
                        quest.getSubQuestId(),
                        groupId,
                        suiteId,
                        sceneId);
                result = false;
                continue;
            }

            // Quest-owned groups must survive the normal visibility unload pass. Mark this before
            // switching suites so a concurrently ticking scene cannot immediately discard the quest
            // entities that are about to be spawned.
            group.dontUnload = true;

            if (!scriptManager.refreshGroupSuite(groupId, suiteId, quest)) {
                Grasscutter.getLogger().warn(
                        "Quest {} failed to refresh group {} suite {} in scene {}",
                        quest.getSubQuestId(),
                        groupId,
                        suiteId,
                        sceneId);
                result = false;
            }
        }

        return result;
    }
}
