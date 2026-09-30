package emu.grasscutter.data.excels.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public final class DungeonDataCityIdTest {
    @Test
    @DisplayName("dungeon city id accepts known resource dump spellings")
    public void cityIdAliasesDecode() {
        for (String field : new String[] {"cityId", "cityID", "CityId", "CityID"}) {
            var dungeon =
                    JsonUtils.decode(
                            "{\"id\":5008,\"" + field + "\":5}", DungeonData.class);
            assertEquals(5, dungeon.getCityId(), field);
        }
    }
}
