package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

@QuestValueExec(QuestExec.QUEST_EXEC_BATCH_SET_OPEN_STATE)
public final class ExecBatchSetOpenState extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... args) {
        int[][] pairs = parsePairs(args);
        if (pairs == null) return false;
        for (var pair : pairs) {
            quest.getOwner().getProgressManager().forceSetOpenState(pair[0], pair[1]);
        }
        return true;
    }

    static int[][] parsePairs(String... args) {
        if (args == null || args.length != 2 || args[0] == null || args[1] == null) return null;
        var ids = args[0].split(",", -1);
        var values = args[1].split(",", -1);
        if (ids.length != values.length) return null;
        var pairs = new int[ids.length][2];
        try {
            // 整批校验通过后再写入，避免只改了一半。
            for (int i = 0; i < ids.length; i++) {
                pairs[i][0] = Integer.parseInt(ids[i].trim());
                pairs[i][1] = Integer.parseInt(values[i].trim());
                if (pairs[i][0] <= 0 || pairs[i][1] < 0) return null;
            }
            return pairs;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
