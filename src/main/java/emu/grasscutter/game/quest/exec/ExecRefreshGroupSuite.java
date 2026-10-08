package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import lombok.val;

@QuestValueExec(QuestExec.QUEST_EXEC_REFRESH_GROUP_SUITE)
public class ExecRefreshGroupSuite extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        return executeWhenReady(quest, paramStr);
    }

    private boolean executeWhenReady(GameQuest quest, String[] paramStr) {
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
            if (!scriptManager.isInitAttempted() && !scriptManager.isDestroyed()) {
                var stateAtDispatch = quest.getState();
                Grasscutter.getLogger().debug(
                        "Quest {} deferring group-suite refresh until scene {} scripts initialize",
                        quest.getSubQuestId(), sceneId);
                scriptManager.whenInitialized(ready -> {
                    if (!ready) {
                        Grasscutter.getLogger().warn(
                                "Quest {} could not refresh group suite: scene {} scripts unavailable",
                                quest.getSubQuestId(), sceneId);
                        return;
                    }
                    // The initialization callback runs on the scene-loading thread. Execute
                    // the scene mutation on its scheduler, not on the loader thread.
                    scene.getScheduler().scheduleDelayedTask(() -> {
                        var world = quest.getOwner().getWorld();
                        if (scriptManager.isDestroyed()
                                || quest.getState() != stateAtDispatch
                                || world == null || world.getSceneById(sceneId) != scene) {
                            Grasscutter.getLogger().debug(
                                    "Dropping obsolete suite refresh: quest {} scene {}",
                                    quest.getSubQuestId(), sceneId);
                            return;
                        }
                        if (!executeWhenReady(quest, paramStr)) {
                            Grasscutter.getLogger().warn(
                                    "Deferred suite refresh failed: quest {} scene {}",
                                    quest.getSubQuestId(), sceneId);
                        }
                    }, 1);
                });
                return true;
            }
            Grasscutter.getLogger().warn(
                    "Quest {} could not refresh group suite: scene {} scripts unavailable",
                    quest.getSubQuestId(), sceneId);
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

            boolean applied = scriptManager.refreshGroupSuite(groupId, suiteId, quest);
            // 35302's Suite 2 contains the combat-training slime (config 439). Report the
            // actual world entity too: an active suite alone does not prove that it spawned.
            if (groupId == 133003002 && suiteId == 2) {
                var slime = scene.getEntityByConfigId(439, groupId);
                Grasscutter.getLogger()
                        .info(
                                "[quest-slime] uid={} sub={} group={} suite={} applied={} spawned={} entityId={}",
                                quest.getOwner().getUid(),
                                quest.getSubQuestId(),
                                groupId, suiteId, applied, slime != null,
                                slime != null ? slime.getId() : 0);
            }
            if (quest.getMainQuestId() >= 351 && quest.getMainQuestId() <= 353) {
                var instance = scriptManager.getGroupInstanceById(groupId);
                Grasscutter.getLogger()
                        .info(
                                "[quest-group] uid={} main={} sub={} scene={} group={} suite={} applied={} activeSuite={}",
                                quest.getOwner().getUid(),
                                quest.getMainQuestId(),
                                quest.getSubQuestId(),
                                sceneId, groupId, suiteId, applied,
                                instance != null ? instance.getActiveSuiteId() : 0);
            }
            if (!applied) {
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
