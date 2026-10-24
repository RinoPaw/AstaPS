package emu.grasscutter.game.managers.blossom;

import com.google.gson.Gson;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.BlossomCampData;
import emu.grasscutter.data.excels.BlossomRefreshExcelConfigData;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.SpawnDataEntry;
import emu.grasscutter.game.world.SpawnDataEntry.SpawnGroupEntry;
import emu.grasscutter.scripts.data.SceneGadget;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.utils.JsonUtils;
import java.util.*;

/**
 * Authoritative 7.1 ley-line camp locations and next-camp edges.
 * BlossomGroups.newGroupVec refers to the original Lua scene group, which has
 * separate gold/blue worktops at the same authored location.
 */
final class BlossomNativeCamps {
    record Key(int cityId, BlossomType kind) {}
    private record Source(int campId, int refreshId, SpawnDataEntry entry,
                          SceneGadget gadget) {}

    private static final Gson GSON = new Gson();
    private final Map<String, Source> resolved = new HashMap<>();
    private final Map<SpawnDataEntry, Source> byEntry = new IdentityHashMap<>();
    private final Set<Integer> unresolvedGroups = new HashSet<>();

    static int initialId(int cityId, BlossomType type, List<BlossomCampData> eligible) {
        if (cityId == 1) {
            // Both IDs exist in the native BlossomGroups graph. Preserve our initial
            // city-1 sources when replacing the incomplete legacy spawn candidate pool.
            return type.getCircleCampId();
        }
        if (eligible.isEmpty()) return 0;
        // No native starting-node selection has yet been recovered for other cities.
        // Choose distinct authored groups deterministically until that rule is proven.
        return eligible.get(type == BlossomType.BLUE && eligible.size() > 1 ? 1 : 0).getId();
    }

    /** Resolve only the selected native camp, without searching fallback nodes. */
    private Source selectedCamp(
            Scene scene, int campId, int city, int rank, BlossomType kind, int refreshId) {
        var camp = GameData.getBlossomCampDataMap().get(campId);
        int type = kind == BlossomType.GOLD ? 1 : 3;
        if (camp == null || camp.getCityId() != city || camp.getLimitLevel() > rank
                || camp.getRefreshTypeVec() == null || !camp.getRefreshTypeVec().contains(type)) {
            return null;
        }
        return resolve(scene, camp, kind, refreshId);
    }

    List<SpawnDataEntry> select(Scene scene, int rank, Collection<Integer> unlockedAreas) {
        var rows = GameData.getBlossomCampDataMap();
        if (rows.isEmpty()) return List.of();
        var cities = GameData.getCityDataMap().values();
        var refreshes = new ArrayList<>(GameData.getBlossomRefreshExcelConfigDataMap().values());
        refreshes.sort(Comparator.comparingInt(BlossomRefreshExcelConfigData::getId));
        var selected = new ArrayList<SpawnDataEntry>();
        var host = scene.getWorld().getHost();

        for (var refresh : refreshes) {
            BlossomType kind = switch (refresh.getRefreshType()) {
                case "BLOSSOM_REFRESH_SCOIN" -> BlossomType.GOLD;
                case "BLOSSOM_REFRESH_EXP" -> BlossomType.BLUE;
                default -> null;
            };
            if (kind == null || refresh.getRefreshCount() <= 0
                    || !BlossomManager.refreshEligible(refresh, rank, unlockedAreas, cities)) continue;

            int city = refresh.getCityId();
            var cityData = GameData.getCityDataMap().get(city);
            if (cityData == null || cityData.getSceneId() != scene.getId()) continue;
            int refreshType = kind == BlossomType.GOLD ? 1 : 3;
            var eligible = rows.values().stream()
                    .filter(camp -> camp.getCityId() == city
                            && camp.getNewGroupVec() != null && !camp.getNewGroupVec().isEmpty()
                            && camp.getRefreshTypeVec() != null
                            && camp.getRefreshTypeVec().contains(refreshType)
                            && camp.getLimitLevel() <= rank)
                    .sorted(Comparator.comparingInt(BlossomCampData::getId))
                    .toList();
            if (eligible.isEmpty()) continue;

            int savedId = host.getBlossomCampId(refresh.getId());
            int initial = initialId(city, kind, eligible);
            var source = selectedCamp(scene, savedId > 0 ? savedId : initial,
                    city, rank, kind, refresh.getId());
            if (source == null) continue;
            var position = source.entry().getPos();
            if (selected.stream().anyMatch(existing ->
                    existing.getPos().computeDistance(position) < 2.5f)) continue;

            selected.add(source.entry());
            if (savedId != source.campId()) host.setBlossomCampId(refresh.getId(), source.campId());
        }
        return selected;
    }

    private Source resolve(Scene scene, BlossomCampData camp, BlossomType kind, int refreshId) {
        String key = camp.getId() + ":" + kind.name();
        Source existing = resolved.get(key);
        if (existing != null) return existing;
        for (int groupId : camp.getNewGroupVec()) {
            if (unresolvedGroups.contains(groupId)) continue;
            var group = SceneGroup.of(groupId).load(scene.getId());
            if (group.gadgets == null) {
                if (unresolvedGroups.add(groupId)) {
                    Grasscutter.getLogger().warn("Ley-line original Lua group missing scene={} group={}",
                            scene.getId(), groupId);
                }
                continue;
            }
            if (!BlossomScriptController.hasNativeEncounter(group)) {
                if (unresolvedGroups.add(groupId)) {
                    Grasscutter.getLogger().warn(
                            "Ley-line original Lua encounter incomplete scene={} group={}",
                            scene.getId(), groupId);
                }
                continue;
            }
            var gadget = group.gadgets.values().stream()
                    .filter(item -> item.gadget_id == kind.getGadgetId())
                    .min(Comparator.comparingInt(item -> item.config_id))
                    .orElse(null);
            if (gadget == null) continue;
            String json = GSON.toJson(Map.of(
                    "sceneId", scene.getId(),
                    "groupId", groupId,
                    "spawns", List.of(Map.of(
                            "gadgetId", gadget.gadget_id,
                            "configId", gadget.config_id,
                            "pos", gadget.pos,
                            "rot", gadget.rot))));
            SpawnGroupEntry entry = JsonUtils.decode(json, SpawnGroupEntry.class);
            var source = entry.getSpawns().get(0);
            source.setGroup(entry);
            Source result = new Source(camp.getId(), refreshId, source, gadget);
            resolved.put(key, result);
            byEntry.put(source, result);
            return result;
        }
        return null;
    }

    boolean owns(SpawnDataEntry entry) {
        return byEntry.containsKey(entry);
    }

    /** Expose the actual authored Lua group for encounter callbacks. */
    SceneGroup groupFor(SpawnDataEntry entry) {
        var source = byEntry.get(entry);
        return source == null ? null : source.gadget().group;
    }

    /** Create the outcrop from the registered group instance and its actual scene block. */
    EntityGadget createGadget(Scene scene, SpawnDataEntry entry) {
        var source = byEntry.get(entry);
        if (source == null) return null;
        var instance = scene.getScriptManager().getGroupInstanceById(source.gadget().group.id);
        if (instance == null) return null;
        var group = instance.getLuaGroup();
        if (group == null || group.gadgets == null) return null;
        var gadget = group.gadgets.get(source.gadget().config_id);
        if (gadget == null || gadget.gadget_id != entry.getGadgetId()) return null;
        return scene.getScriptManager().createGadget(group.id, group.block_id, gadget);
    }

    int campId(SpawnDataEntry entry) {
        Source source = byEntry.get(entry);
        return source == null ? 0 : source.campId();
    }

    int refreshId(SpawnDataEntry entry) {
        Source source = byEntry.get(entry);
        return source == null ? 0 : source.refreshId();
    }

    /** Advance only after a successful reward claim; the saved cursor survives scene reload. */
    boolean advance(Scene scene, SpawnDataEntry entry, int rank) {
        var source = byEntry.get(entry);
        if (source == null) return false;
        var camp = GameData.getBlossomCampDataMap().get(source.campId());
        if (camp == null || camp.getNextCampIdVec() == null || camp.getNextCampIdVec().isEmpty())
            return false;
        var kind = BlossomType.valueOf(entry.getGadgetId());
        if (kind == null) return false;
        // Persist the original edge even when its Lua is unavailable.
        int nextId = camp.getNextCampIdVec().get(0);
        if (!GameData.getBlossomCampDataMap().containsKey(nextId)) return false;
        scene.getWorld().getHost().setBlossomCampId(source.refreshId(), nextId);
        Grasscutter.getLogger().info("Ley-line camp advanced city={} kind={} {} -> {}",
                camp.getCityId(), kind, source.campId(), nextId);
        return true;
    }
}
