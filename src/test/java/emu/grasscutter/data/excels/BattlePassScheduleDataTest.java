package emu.grasscutter.data.excels;

import static org.junit.jupiter.api.Assertions.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.battlepass.SafeBattlePassSchedule;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;

public class BattlePassScheduleDataTest {
    @Test
    public void selectsCurrentResourceScheduleAndFallsBackWithoutResources() {
        var schedules = GameData.getBattlePassScheduleDataMap();
        var saved = new Int2ObjectOpenHashMap<>(schedules);
        try {
            schedules.clear();
            assertEquals(6700, BattlePassScheduleData.currentId());
            for (int id : new int[] {6700, 7000, 7100, 7200}) {
                schedules.put(id, JsonUtils.decode("{\"id\":" + id + "}", BattlePassScheduleData.class));
            }
            assertEquals(7100, BattlePassScheduleData.currentId());
            assertEquals(7100, SafeBattlePassSchedule.build(null).getScheduleId());
        } finally {
            schedules.clear();
            schedules.putAll(saved);
        }
    }
}
