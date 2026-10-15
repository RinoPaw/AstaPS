package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import emu.grasscutter.data.excels.CityData;
import emu.grasscutter.data.excels.world.WorldAreaData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RewardAreaAttributesTest {
    private static CityData city(int id, int... areas) {
        var result = new CityData();
        result.setCityId(id);
        result.setAreaIdVec(java.util.Arrays.stream(areas).boxed().toList());
        return result;
    }

    private static WorldAreaData area(int id, int scene) {
        return new Gson().fromJson(
                "{\"areaID1\":" + id + ",\"sceneID\":" + scene + "}",
                WorldAreaData.class);
    }

    @Test
    void nativeAreaMapsToItsCity() {
        var props = new HashMap<String, String>();
        RewardAreaAttributes.add(props, 3, 10, area(10, 3),
                List.of(city(1, 1, 10), city(2, 5, 8)));
        assertEquals(Map.of("area", "10", "city", "1"), props);
        props.clear();
        RewardAreaAttributes.add(props, 3, 8, area(8, 3),
                List.of(city(1, 1), city(2, 8)));
        assertEquals(Map.of("area", "8", "city", "2"), props);
    }

    @Test
    void mismatchedSceneAndAmbiguousCityAreNotUsed() {
        var props = new HashMap<String, String>();
        RewardAreaAttributes.add(props, 7, 10, area(10, 3), List.of(city(1, 10)));
        assertTrue(props.isEmpty());
        RewardAreaAttributes.add(props, 3, 10, area(10, 3),
                List.of(city(1, 10), city(2, 10)));
        assertTrue(props.isEmpty());
    }
}
