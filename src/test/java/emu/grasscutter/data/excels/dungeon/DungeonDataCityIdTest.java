package emu.grasscutter.data.excels.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public final class DungeonDataCityIdTest {
    @Test
    @DisplayName("dungeon city id reads the current resource field")
    public void currentCityIdDecodes() {
        var dungeon = JsonUtils.decode("{\"id\":5008,\"cityId\":5}", DungeonData.class);
        assertEquals(5, dungeon.getCityId());
    }
}
