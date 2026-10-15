package emu.grasscutter.game.reward;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.CityData;
import emu.grasscutter.data.excels.world.WorldAreaData;
import java.util.Collection;
import java.util.Map;

/** Adds original area and city identifiers for reward CSS selection. */
public final class RewardAreaAttributes {
    private RewardAreaAttributes() {}

    public static void add(Map<String, String> attributes, int sceneId, int areaId) {
        add(attributes, sceneId, areaId,
                GameData.getWorldAreaDataMap().get(areaId),
                GameData.getCityDataMap().values());
    }

    static void add(Map<String, String> attributes, int sceneId, int areaId,
            WorldAreaData worldArea, Collection<CityData> cities) {
        if (worldArea == null || worldArea.getSceneId() != sceneId) return;
        int area = worldArea.getParentArea();
        if (area <= 0) return;
        int city = 0;
        for (CityData entry : cities) {
            if (entry.getAreaIdVec() == null || !entry.getAreaIdVec().contains(area)) {
                continue;
            }
            if (city != 0 && city != entry.getCityId()) return;
            city = entry.getCityId();
        }
        attributes.put("area", Integer.toString(area));
        if (city != 0) attributes.put("city", Integer.toString(city));
    }
}
