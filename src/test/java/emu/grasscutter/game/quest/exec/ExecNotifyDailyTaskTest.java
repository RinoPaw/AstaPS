package emu.grasscutter.game.quest.exec;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.game.dailytask.DailyTaskManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.DailyTaskProgressNotifyOuterClass.DailyTaskProgressNotify;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

final class ExecNotifyDailyTaskTest {
    @Test
    void sendsTheActiveCommissionsStateWithoutCompletingIt() throws Exception {
        var manager =
                new Gson()
                        .fromJson(
                                """
                                {"dailyTasks":[{"taskId":20500,"finishProgress":2,"progress":1}]}
                                """,
                                DailyTaskManager.class);
        List<BasePacket> sent = new ArrayList<>();
        var player =
                new Player() {
                    @Override
                    public DailyTaskManager getDailyTaskManager() {
                        return manager;
                    }

                    @Override
                    public void sendPacket(BasePacket packet) {
                        sent.add(packet);
                    }
                };
        var quest =
                new GameQuest() {
                    @Override
                    public Player getOwner() {
                        return player;
                    }
                };
        var handler = new ExecNotifyDailyTask();
        assertTrue(handler.execute(quest, null, "20500"));
        assertEquals(1, sent.size());
        var notify = DailyTaskProgressNotify.parseFrom(sent.get(0).getData());
        assertEquals(20500, notify.getInfo().getDailyTaskId());
        assertEquals(1, manager.getDailyTask(20500).getProgress());
        assertFalse(manager.getDailyTask(20500).isFinished());
        assertFalse(handler.execute(quest, null, "20501"));
        assertFalse(handler.execute(quest, null, "bad"));
        assertEquals(1, sent.size());
    }
}
