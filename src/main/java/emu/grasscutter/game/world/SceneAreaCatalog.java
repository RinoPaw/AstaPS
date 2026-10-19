package emu.grasscutter.game.world;

import emu.grasscutter.data.GameData;
import java.util.Set;
import java.util.TreeSet;

/** Real map-area IDs from loaded scene-point and world-area resources. */
public final class SceneAreaCatalog {
    private SceneAreaCatalog() {}

    public static Set<Integer> forScene(int sceneId) {
        var ids = new TreeSet<Integer>();
        if (sceneId <= 0) return ids;

        for (var area : GameData.getWorldAreaDataMap().values()) {
            if (area.getSceneId() != sceneId) continue;
            if (area.getParentArea() > 0) ids.add(area.getParentArea());
            if (area.getChildArea() > 0) ids.add(area.getChildArea());
        }

        var points = GameData.getScenePointsPerScene().get(sceneId);
        if (points != null) {
            for (int pointId : points) {
                var entry = GameData.getScenePointEntryById(sceneId, pointId);
                if (entry != null && entry.getPointData() != null
                        && entry.getPointData().getAreaId() > 0) {
                    ids.add(entry.getPointData().getAreaId());
                }
            }
        }
        return ids;
    }
}
