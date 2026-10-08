package emu.grasscutter.game.quest.exec;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import emu.grasscutter.scripts.constants.EventType;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class QuestExecDispatchStateTest {
    @Test
    void notifyEventTypeComesFromCapturedState() {
        assertEquals(EventType.EVENT_QUEST_START,
                ExecNotifyGroupLua.eventTypeFor(QuestState.QUEST_STATE_UNFINISHED));
        assertEquals(EventType.EVENT_QUEST_FINISH,
                ExecNotifyGroupLua.eventTypeFor(QuestState.QUEST_STATE_FINISHED));
    }

    @Test
    void ordinaryHandlersKeepTheirOriginalExecutionPath() {
        var observed = new AtomicReference<String>();
        QuestExecHandler legacy = new QuestExecHandler() {
            @Override
            public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... args) {
                observed.set(args[0]);
                return true;
            }
        };
        assertTrue(legacy.execute(null, null, QuestState.QUEST_STATE_UNFINISHED, "legacy"));
        assertEquals("legacy", observed.get());
    }
}
