package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.exec.*;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The combat, dialogue and reward actions used by the prologue need real handler classes. */
final class QuestRuntimeActionCoverageTest {
    @Test
    void reviewedCombatDialogAndRewardActionsHaveHandlers() {
        var required = Map.<QuestExec, Class<? extends QuestExecHandler>>of(
                QuestExec.QUEST_EXEC_REFRESH_GROUP_SUITE, ExecRefreshGroupSuite.class,
                QuestExec.QUEST_EXEC_NOTIFY_GROUP_LUA, ExecNotifyGroupLua.class,
                QuestExec.QUEST_EXEC_ADD_CUR_AVATAR_ENERGY, ExecAddCurAvatarEnergy.class,
                QuestExec.QUEST_EXEC_GRANT_TRIAL_AVATAR, ExecGrantTrialAvatar.class,
                QuestExec.QUEST_EXEC_DEL_PACK_ITEM, ExecDelPackItem.class,
                QuestExec.QUEST_EXEC_ADD_QUEST_PROGRESS, ExecAddQuestProgress.class,
                QuestExec.QUEST_EXEC_UNLOCK_POINT, ExecUnlockPoint.class,
                QuestExec.QUEST_EXEC_ROLLBACK_QUEST, ExecRollbackQuest.class,
                QuestExec.QUEST_EXEC_REMOVE_TRIAL_AVATAR, ExecRemoveTrialAvatar.class);
        for (var entry : required.entrySet()) {
            var annotation = entry.getValue().getAnnotation(QuestValueExec.class);
            assertNotNull(annotation, "No registration annotation for " + entry.getKey());
            assertEquals(entry.getKey(), annotation.value());
        }
    }
}
