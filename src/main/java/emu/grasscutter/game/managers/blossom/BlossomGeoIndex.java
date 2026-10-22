package emu.grasscutter.game.managers.blossom;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.data.GameData;
import emu.grasscutter.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Authored level-1 WorldArea polygons, used to associate scene positions with a native city ID. */
final class BlossomGeoIndex {
    private record Point(double x, double z) {}

    private record Area(int parentId, double minX, double maxX, double minZ, double maxZ,
                        List<Point> boundary) {
        boolean contains(double x, double z) {
            if (x < minX || x > maxX || z < minZ || z > maxZ) return false;
            boolean inside = false;
            for (int i = 0, j = boundary.size() - 1; i < boundary.size(); j = i++) {
                Point a = boundary.get(i), b = boundary.get(j);
                if ((a.z() > z) != (b.z() > z)
                        && x < (b.x() - a.x()) * (z - a.z()) / (b.z() - a.z()) + a.x()) {
                    inside = !inside;
                }
            }
            return inside;
        }
    }

    private final List<Area> areas;
    private final Map<Integer, Integer> cityForParent;

    private BlossomGeoIndex(List<Area> areas, Map<Integer, Integer> cityForParent) {
        this.areas = areas;
        this.cityForParent = cityForParent;
    }

    int cityId(double x, double z) {
        int cityId = 0;
        for (var area : areas) {
            if (!area.contains(x, z)) continue;
            int candidate = cityForParent.getOrDefault(area.parentId(), 0);
            if (candidate == 0) continue;
            if (cityId != 0 && cityId != candidate) return 0;
            cityId = candidate;
        }
        return cityId;
    }

    static BlossomGeoIndex load(int sceneId) {
        var path = FileUtils.getScriptPath(
                "Scene/" + sceneId + "/scene" + sceneId + "_world_areas.json");
        try (var reader = Files.newBufferedReader(path)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            Map<Integer, Integer> cityForParent = new HashMap<>();
            for (var city : GameData.getCityDataMap().values()) {
                if (city.getAreaIdVec() == null) continue;
                for (int parentId : city.getAreaIdVec()) {
                    Integer previous = cityForParent.putIfAbsent(parentId, city.getCityId());
                    if (previous != null && previous != city.getCityId()) {
                        throw new IllegalStateException(
                                "Native city area " + parentId + " belongs to multiple cities");
                    }
                }
            }
            return parse(root, cityForParent);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot load level-1 world areas for scene " + sceneId + " from " + path, e);
        }
    }

    static BlossomGeoIndex parse(JsonObject root, Map<Integer, Integer> cityForParent) {
        var areas = new ArrayList<Area>();
        for (var item : root.getAsJsonArray("level1Areas")) {
            var level1 = item.getAsJsonObject().getAsJsonObject("level1Area");
            int parentId = level1.get("id1").getAsInt();
            if (!cityForParent.containsKey(parentId)) continue;
            var polygon = level1.getAsJsonObject("polygonData");
            var min = polygon.getAsJsonObject("minArea");
            var max = polygon.getAsJsonObject("maxArea");
            var vertices = new ArrayList<Point>();
            for (var vertex : polygon.getAsJsonArray("roadPoints")) {
                var v = vertex.getAsJsonObject();
                vertices.add(new Point(v.get("x").getAsDouble(), v.get("z").getAsDouble()));
            }
            if (vertices.size() < 3) continue;
            areas.add(new Area(parentId, min.get("x").getAsDouble(), max.get("x").getAsDouble(),
                    min.get("z").getAsDouble(), max.get("z").getAsDouble(), List.copyOf(vertices)));
        }
        return new BlossomGeoIndex(List.copyOf(areas), Map.copyOf(cityForParent));
    }
}
