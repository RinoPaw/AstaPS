package emu.grasscutter.game.quest.content;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.quest.QuestData;
import org.junit.jupiter.api.Test;

/** Malformed client plot-content events must never stop an entire event batch. */
final class PlotContentEventGuardTest {
    private static QuestData.QuestContentCondition expected(int id) {
        var condition = new QuestData.QuestContentCondition();
        condition.setParam(new int[]{id, 0});
        return condition;
    }

    @Test
    void finishedPlotDoesNotReadMissingOrForeignEventId() {
        var handler = new ContentFinishPlot();
        assertFalse(handler.execute(null, expected(35104), ""));
        assertFalse(handler.execute(null, expected(35104), "", 35203));
        assertFalse(handler.execute(null, null, "", 35104));
        var missingId = expected(35104);
        missingId.setParam(new int[0]);
        assertFalse(handler.execute(null, missingId, "", 35104));
    }

    @Test
    void failedPlotDoesNotReadMissingOrForeignEventId() {
        var handler = new ContentNotFinishPlot();
        assertFalse(handler.execute(null, expected(35203), ""));
        assertFalse(handler.execute(null, expected(35203), "", 35205));
        assertFalse(handler.execute(null, null, "", 35203));
        var missingId = expected(35203);
        missingId.setParam(new int[0]);
        assertFalse(handler.execute(null, missingId, "", 35203));
    }
}
