package emu.grasscutter.game.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.utils.FileUtils;
import java.io.Reader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Level-1 and level-2 world area lookup by position, from
 * {@code BinOutput/Scene/WorldArea/scene<N>_worldArea.json}.
 *
 * <p>Level-2 polygons can overlap on X/Z and have different Y limits. Do not use a 2-D lookup
 * for level-2 areas. Resource fields in 7.1:
 *
 * <pre>
 * OBKNAJCIHEA[]              entries for level-1 areas
 *   LKBLPFHOAFB              level-1 record
 *     BBLLBIOLMHP            areaID1
 *     JLFEAGHGCLP            outline
 *   OPHHFKKPLDI[]            level-2 outlines below this level-1 area
 *     BBLLBIOLMHP            parent areaID1
 *     EOIFEEDBAFJ            areaID2
 *     JLFEAGHGCLP            outline and optional height limits
 *       BFLLDPOAJGD[]        polygon points {_x, _z}
 *       MHLOLALFHHF          optional lower bound {_y}
 *       NFFDCMLAAFO          optional upper bound {_y}
 * </pre>
 *
 * <p>Some level-2 IDs in WorldAreaConfigData.json have no outline. They resolve to level 2 = 0
 * instead of a guessed neighboring area. The existing level-1 lookup remains 2-D.
 */
public final class WorldAreaIndex {
    private static final String AREA_LIST = "OBKNAJCIHEA";
    private static final String LEVEL1 = "LKBLPFHOAFB";
    private static final String LEVEL2_LIST = "OPHHFKKPLDI";
    private static final String AREA_ID1 = "BBLLBIOLMHP";
    private static final String AREA_ID2 = "EOIFEEDBAFJ";
    private static final String OUTLINE = "JLFEAGHGCLP";
    private static final String POINTS = "BFLLDPOAJGD";
    private static final String LOWER_BOUND = "MHLOLALFHHF";
    private static final String UPPER_BOUND = "NFFDCMLAAFO";

    /** Missing or unreadable scenes cache an empty index so the file is not re-read on every move. */
    private static final Map<Integer, WorldAreaIndex> CACHE = new ConcurrentHashMap<>();

    /** Level-2 ID is zero when the position has no matching level-2 outline. */
    public record AreaIds(int level1, int level2) {}

    private record Region(Area level1, List<Area> level2) {}

    private final List<Region> regions;

    private WorldAreaIndex(List<Region> regions) {
        this.regions = List.copyOf(regions);
    }

    public static WorldAreaIndex forScene(int sceneId) {
        return CACHE.computeIfAbsent(sceneId, WorldAreaIndex::load);
    }

    /** Whether any level-1 outline was loaded for this scene. */
    public boolean isEmpty() {
        return this.regions.isEmpty();
    }

    /** Existing 2-D level-1 lookup. A value of 0 means no matching polygon. */
    public int level1AreaAt(float x, float z) {
        for (Region region : this.regions) {
            if (region.level1.contains(x, z)) return region.level1.id;
        }
        return 0;
    }

    /**
     * Returns the matching level-1 and level-2 IDs for a world-space position.
     * When level 1 is known but level 2 has no matching boundary, only level 2 is zero.
     */
    public AreaIds areaAt(float x, float y, float z) {
        for (Region region : this.regions) {
            if (!region.level1.contains(x, z)) continue;
            for (Area subarea : region.level2) {
                if (subarea.contains(x, y, z)) return new AreaIds(region.level1.id, subarea.id);
            }
            return new AreaIds(region.level1.id, 0);
        }
        return new AreaIds(0, 0);
    }

    public int level2AreaAt(float x, float y, float z) {
        return areaAt(x, y, z).level2();
    }

    private static WorldAreaIndex load(int sceneId) {
        var path = FileUtils.getResourcePath("BinOutput/Scene/WorldArea/scene" + sceneId + "_worldArea.json");
        if (!Files.isRegularFile(path)) {
            Grasscutter.getLogger().debug("[WorldArea] no outline data for scene {} at {}", sceneId, path);
            return new WorldAreaIndex(List.of());
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            var index = parse(JsonParser.parseReader(reader));
            int level2Polygons = index.regions.stream().mapToInt(region -> region.level2.size()).sum();
            Grasscutter.getLogger()
                    .debug("[WorldArea] loaded {} level-1 and {} level-2 polygons for scene {}",
                            index.regions.size(), level2Polygons, sceneId);
            return index;
        } catch (Exception e) {
            Grasscutter.getLogger().warn("Failed to read world areas for scene {}", sceneId, e);
            return new WorldAreaIndex(List.of());
        }
    }

    static WorldAreaIndex parse(JsonElement root) {
        var regions = new ArrayList<Region>();
        if (root == null || !root.isJsonObject()) return new WorldAreaIndex(regions);
        JsonArray entries = asArray(root.getAsJsonObject().get(AREA_LIST));
        if (entries == null) return new WorldAreaIndex(regions);

        for (JsonElement entry : entries) {
            if (!entry.isJsonObject()) continue;
            JsonObject parent = entry.getAsJsonObject();
            Area level1 = parseArea(asObject(parent.get(LEVEL1)), AREA_ID1);
            if (level1 == null) continue;

            var subareas = new ArrayList<Area>();
            JsonArray nested = asArray(parent.get(LEVEL2_LIST));
            if (nested != null) {
                for (JsonElement child : nested) {
                    JsonObject object = asObject(child);
                    if (positiveId(object, AREA_ID1) != level1.id) continue;
                    Area level2 = parseArea(object, AREA_ID2);
                    if (level2 != null) subareas.add(level2);
                }
            }
            // Preserve separate polygons for the same area ID; they are not necessarily contiguous.
            regions.add(new Region(level1, List.copyOf(subareas)));
        }
        return new WorldAreaIndex(regions);
    }

    private static Area parseArea(JsonObject source, String idField) {
        int id = positiveId(source, idField);
        if (id <= 0) return null;
        JsonObject outline = asObject(source.get(OUTLINE));
        JsonArray points = outline == null ? null : asArray(outline.get(POINTS));
        if (points == null || points.size() < 3) return null;

        float[] xs = new float[points.size()];
        float[] zs = new float[points.size()];
        int count = 0;
        for (JsonElement element : points) {
            JsonObject point = asObject(element);
            if (point == null || !isNumber(point.get("_x"))
                    || !isNumber(point.get("_z"))) continue;
            float x = point.get("_x").getAsFloat();
            float z = point.get("_z").getAsFloat();
            if (!Float.isFinite(x) || !Float.isFinite(z)) continue;
            xs[count] = x;
            zs[count] = z;
            count++;
        }
        if (count < 3) return null;

        float minY = boundY(outline, LOWER_BOUND, Float.NEGATIVE_INFINITY);
        float maxY = boundY(outline, UPPER_BOUND, Float.POSITIVE_INFINITY);
        if (minY > maxY) return null;
        return new Area(id, xs, zs, count, minY, maxY);
    }

    private static float boundY(JsonObject outline, String key, float fallback) {
        JsonObject bound = asObject(outline.get(key));
        if (bound == null || !isNumber(bound.get("_y"))) return fallback;
        float value = bound.get("_y").getAsFloat();
        return Float.isFinite(value) ? value : fallback;
    }

    private static boolean isNumber(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
    }

    private static int positiveId(JsonObject object, String key) {
        if (object == null || !isNumber(object.get(key))) return 0;
        return Math.max(object.get(key).getAsInt(), 0);
    }

    private static JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray asArray(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    static final class Area {
        final int id;
        private final float[] xs;
        private final float[] zs;
        private final int n;
        private final float minX, maxX, minZ, maxZ, minY, maxY;

        Area(int id, float[] xs, float[] zs, int n, float minY, float maxY) {
            this.id = id;
            this.xs = xs;
            this.zs = zs;
            this.n = n;
            this.minY = minY;
            this.maxY = maxY;
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                minX = Math.min(minX, xs[i]);
                maxX = Math.max(maxX, xs[i]);
                minZ = Math.min(minZ, zs[i]);
                maxZ = Math.max(maxZ, zs[i]);
            }
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        boolean contains(float x, float y, float z) {
            return y >= minY && y <= maxY && contains(x, z);
        }

        /** Even-odd ray cast, after a bounding-box reject that settles almost every area. */
        boolean contains(float x, float z) {
            if (x < minX || x > maxX || z < minZ || z > maxZ) return false;
            boolean inside = false;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                if ((zs[i] > z) != (zs[j] > z)
                        && x < (xs[j] - xs[i]) * (z - zs[i]) / (zs[j] - zs[i]) + xs[i]) {
                    inside = !inside;
                }
            }
            return inside;
        }
    }
}
