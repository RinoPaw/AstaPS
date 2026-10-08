package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.exec.*;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** All implemented action opcodes found in the 7.1 Mondstadt prologue resource scope. */
final class QuestRuntimeActionCoverageTest {
    @Test
    void reviewedPrologueActionsHaveConcreteRegisteredHandlers() {
        var handlers = Map.<QuestExec, Class<? extends QuestExecHandler>>ofEntries(
                Map.entry(QuestExec.QUEST_EXEC_REFRESH_GROUP_SUITE, ExecRefreshGroupSuite.class),
                Map.entry(QuestExec.QUEST_EXEC_NOTIFY_GROUP_LUA, ExecNotifyGroupLua.class),
                Map.entry(QuestExec.QUEST_EXEC_ADD_CUR_AVATAR_ENERGY, ExecAddCurAvatarEnergy.class),
                Map.entry(QuestExec.QUEST_EXEC_GRANT_TRIAL_AVATAR, ExecGrantTrialAvatar.class),
                Map.entry(QuestExec.QUEST_EXEC_DEL_PACK_ITEM, ExecDelPackItem.class),
                Map.entry(QuestExec.QUEST_EXEC_DEL_PACK_ITEM_BATCH, ExecDelPackItemBatch.class),
                Map.entry(QuestExec.QUEST_EXEC_ADD_QUEST_PROGRESS, ExecAddQuestProgress.class),
                Map.entry(QuestExec.QUEST_EXEC_UNLOCK_POINT, ExecUnlockPoint.class),
                Map.entry(QuestExec.QUEST_EXEC_LOCK_POINT, ExecLockPoint.class),
                Map.entry(QuestExec.QUEST_EXEC_UNLOCK_AREA, ExecUnlockArea.class),
                Map.entry(QuestExec.QUEST_EXEC_ROLLBACK_QUEST, ExecRollbackQuest.class),
                Map.entry(QuestExec.QUEST_EXEC_REMOVE_TRIAL_AVATAR, ExecRemoveTrialAvatar.class),
                Map.entry(QuestExec.QUEST_EXEC_SET_IS_GAME_TIME_LOCKED, ExecSetGameTimeLocked.class),
                Map.entry(QuestExec.QUEST_EXEC_SET_IS_WEATHER_LOCKED, ExecSetWeatherLocked.class),
                Map.entry(QuestExec.QUEST_EXEC_SET_IS_FLYABLE, ExecSetFlyable.class),
                Map.entry(QuestExec.QUEST_EXEC_CHANGE_AVATAR_ELEMET, ExecChangeAvatarElemet.class),
                Map.entry(QuestExec.QUEST_EXEC_SET_OPEN_STATE, ExecSetOpenState.class),
                Map.entry(QuestExec.QUEST_EXEC_SET_QUEST_GLOBAL_VAR, ExecSetQuestGlobalVar.class),
                Map.entry(QuestExec.QUEST_EXEC_REFRESH_GROUP_MONSTER, ExecRefreshGroupMonster.class),
                Map.entry(QuestExec.QUEST_EXEC_SET_WEATHER_GADGET, ExecSetWeatherGadget.class));
        assertEquals(20, handlers.size());
        for (var entry : handlers.entrySet()) {
            var annotation = entry.getValue().getAnnotation(QuestValueExec.class);
            assertNotNull(annotation, "No registration annotation for " + entry.getKey());
            assertEquals(entry.getKey(), annotation.value());
        }
        // All 20 action types observed in the paired 7.1 resource scope
        // must have a concrete opcode-annotated handler.
        assertEquals(ExecSetWeatherGadget.class,
                handlers.get(QuestExec.QUEST_EXEC_SET_WEATHER_GADGET));
    }
}
