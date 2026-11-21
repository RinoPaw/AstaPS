package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import emu.grasscutter.server.packet.send.PacketDailyTaskProgressNotify;

@QuestValueExec(QuestExec.QUEST_EXEC_NOTIFY_DAILY_TASK)
public final class ExecNotifyDailyTask extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... args) {
        if (args == null || args.length != 1) return false;
        final int taskId;
        try {
            taskId = Integer.parseInt(args[0]);
        } catch (NumberFormatException ignored) {
            return false;
        }
        var player = quest.getOwner();
        var manager = player.getDailyTaskManager();
        var task = manager == null ? null : manager.getDailyTask(taskId);
        if (task == null) return false;
        // 这里只推送现有进度，不能把通知当成完成任务。
        player.sendPacket(new PacketDailyTaskProgressNotify(task));
        return true;
    }
}
