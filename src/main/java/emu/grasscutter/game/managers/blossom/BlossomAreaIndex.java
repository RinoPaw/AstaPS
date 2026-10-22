package emu.grasscutter.game.managers.blossom;

import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.utils.FileUtils;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Original scene WorldArea polygon exports, matched to CityConfig.areaIdVec. */
public final class BlossomAreaIndex {
    private BlossomAreaIndex() {}
    private record Region(int city, double[] x, double[] z) {}
    private static final Map<Integer, List<Region>> REGIONS = new ConcurrentHashMap<>();

    public static int cityFor(int scene, Position pos) {
        int city = 0;
        for (var region : REGIONS.computeIfAbsent(scene, BlossomAreaIndex::readScene)) {
            if (!contains(region.x(), region.z(), pos.getX(), pos.getZ())) continue;
            if (city != 0 && city != region.city()) return 0;
            city = region.city();
        }
        return city;
    }

    static boolean contains(double[] x, double[] z, double px, double pz) {
        boolean inside = false;
        for (int i = 0, j = x.length - 1; i < x.length; j = i++) {
            if ((z[i] > pz) != (z[j] > pz)
                    && px < (x[j] - x[i]) * (pz - z[i]) / (z[j] - z[i]) + x[i]) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static List<Region> readScene(int scene) {
        var cities = new HashMap<Integer, Integer>();
        for (var c : GameData.getCityDataMap().values()) {
            if (c.getAreaIdVec() == null) continue;
            for (int area : c.getAreaIdVec()) {
                var previous = cities.putIfAbsent(area, c.getCityId());
                if (previous != null && previous != c.getCityId()) {
                    throw new IllegalStateException("Ambiguous native city area " + area);
                }
            }
        }
        var result = new ArrayList<Region>();
        var path = FileUtils.getResourcePath(
                "BinOutput/Scene/WorldArea/scene" + scene + "_worldArea.json");
        try (var reader = Files.newBufferedReader(path)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject()
                    .getAsJsonArray("OBKNAJCIHEA");
            for (var part : root) {
                var area = part.getAsJsonObject().getAsJsonObject("LKBLPFHOAFB");
                int city = cities.getOrDefault(area.get("BBLLBIOLMHP").getAsInt(), 0);
                if (city == 0) continue;
                var nodes = area.getAsJsonObject("JLFEAGHGCLP")
                        .getAsJsonArray("BFLLDPOAJGD");
                if (nodes.size() < 3) continue;
                double[] x = new double[nodes.size()], z = new double[nodes.size()];
                for (int i = 0; i < nodes.size(); i++) {
                    var pt = nodes.get(i).getAsJsonObject();
                    x[i] = pt.get("_x").getAsDouble();
                    z[i] = pt.get("_z").getAsDouble();
                }
                result.add(new Region(city, x, z));
            }
        } catch (Exception e) {
            Grasscutter.getLogger().error(
                    "Cannot load WorldArea polygons for ley-line city selection scene={}", scene, e);
        }
        Grasscutter.getLogger().info(
                "Ley-line world-area index scene={} city polygons={}", scene, result.size());
        return List.copyOf(result);
    }
}
