package emu.grasscutter.game.managers.blossom;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.BlossomCampData;
import emu.grasscutter.data.excels.BlossomRefreshExcelConfigData;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.data.SceneGadget;
import emu.grasscutter.scripts.data.SceneGroup;
import java.util.*;
import java.util.function.Predicate;

/**
 * Selects any number of authored camps per country and refresh kind.
 * Refresh rows control kind availability and concurrent count; the worktop's
 * authored area_id controls which individual camps are unlocked.
 */
final class BlossomNativeCamps {
    record Source(int campId, int refreshId, int slot, SceneGadget gadget) {}
    private record CampKind(int campId, int nativeType) {}

    // A numeric tuple prevents ambiguity and never embeds a refresh slot in the Lua cache.
    private final Map<CampKind, SceneGadget> resolved = new HashMap<>();

    static int initialId(List<BlossomCampData> eligible) {
        return eligible.isEmpty() ? 0 : eligible.get(0).getId();
    }

    static int firstAvailableCamp(
            Collection<Integer> starts, Map<Integer, BlossomCampData> camps,
            int city, int nativeType, Set<Integer> occupied,
            Predicate<BlossomCampData> usable) {
        var queue = new ArrayDeque<>(starts);
        var visited = new HashSet<Integer>();
        while (!queue.isEmpty()) {
            int id = queue.removeFirst();
            if (!visited.add(id)) continue;
            var camp = camps.get(id);
            if (camp == null || camp.getCityId() != city) continue;
            if (camp.getNextCampIdVec() != null) queue.addAll(camp.getNextCampIdVec());
            if (occupied.contains(id)
                    || camp.getRefreshTypeVec() == null
                    || !camp.getRefreshTypeVec().contains(nativeType)
                    || camp.getNewGroupVec() == null || camp.getNewGroupVec().isEmpty()) continue;
            if (usable.test(camp)) return id;
        }
        return 0;
    }

    private Source selectedCamp(
            Scene scene, BlossomCampData camp, BlossomType kind, int refreshId, int slot,
            Collection<Integer> unlockedAreas) {
        if (camp.getRefreshTypeVec() == null
                || !camp.getRefreshTypeVec().contains(kind.refreshTypeId())) return null;
        var gadget = resolve(scene, camp, kind);
        // An unlocked region authorizes its camp; there is no city-wide shortcut.
        if (gadget == null || gadget.area_id <= 0
                || !unlockedAreas.contains(gadget.area_id)) return null;
        return new Source(camp.getId(), refreshId, slot, gadget);
    }

    List<Source> select(Scene scene, int rank, Collection<Integer> unlockedAreas) {
        var camps = GameData.getBlossomCampDataMap();
        if (camps.isEmpty()) return List.of();
        var refreshes = new ArrayList<>(GameData.getBlossomRefreshExcelConfigDataMap().values());
        refreshes.sort(Comparator.comparingInt(BlossomRefreshExcelConfigData::getCityId)
                .thenComparingInt(BlossomRefreshExcelConfigData::getId));

        var candidates = new HashMap<Integer, List<BlossomCampData>>();
        for (var camp : camps.values()) {
            if (camp.getNewGroupVec() == null || camp.getNewGroupVec().isEmpty()
                    || camp.getRefreshTypeVec() == null) continue;
            candidates.computeIfAbsent(camp.getCityId(), unused -> new ArrayList<>()).add(camp);
        }
        candidates.values().forEach(list ->
                list.sort(Comparator.comparingInt(BlossomCampData::getId)));

        var host = scene.getWorld().getHost();
        var selected = new ArrayList<Source>();
        var occupiedByCity = new HashMap<Integer, Set<Integer>>();
        var selectedByCity = new HashMap<Integer, List<Source>>();
        for (var refresh : refreshes) {
            var kind = BlossomType.forRefresh(refresh.getRefreshType());
            if (kind == null || refresh.getRefreshCount() <= 0
                    || !BlossomManager.refreshEligible(refresh, rank)) continue;

            var cityData = GameData.getCityDataMap().get(refresh.getCityId());
            if (cityData == null || cityData.getSceneId() != scene.getId()) continue;
            var eligible = candidates.getOrDefault(refresh.getCityId(), List.of()).stream()
                    .filter(camp -> camp.getRefreshTypeVec().contains(kind.refreshTypeId()))
                    .toList();
            if (eligible.isEmpty()) continue;

            var occupied = occupiedByCity.computeIfAbsent(refresh.getCityId(), ignored -> new HashSet<>());
            var local = selectedByCity.computeIfAbsent(refresh.getCityId(), ignored -> new ArrayList<>());
            for (int slot = 0; slot < refresh.getRefreshCount(); slot++) {
                if (host.isBlossomConsumed(refresh.getId(), slot)) continue;
                int savedId = host.getBlossomCampId(refresh.getId(), slot);
                var starts = new ArrayList<Integer>();
                if (savedId > 0) starts.add(savedId);
                for (var camp : eligible) starts.add(camp.getId());
                final int currentSlot = slot;
                int chosen = firstAvailableCamp(
                        starts, camps, refresh.getCityId(), kind.refreshTypeId(), occupied,
                        candidate -> {
                            var source = selectedCamp(
                                    scene, candidate, kind, refresh.getId(), currentSlot, unlockedAreas);
                            return source != null && local.stream().noneMatch(other ->
                                    other.gadget().pos.computeDistance(source.gadget().pos) < 2.5f);
                        });
                if (chosen == 0) continue;
                var source = selectedCamp(
                        scene, camps.get(chosen), kind, refresh.getId(), slot, unlockedAreas);
                selected.add(source);
                local.add(source);
                occupied.add(chosen);
                if (savedId != chosen) host.setBlossomCampId(refresh.getId(), slot, chosen);
            }
        }
        return selected;
    }

    /** Lua is the encounter authority; only the selected, authored worktop is needed here. */
    private SceneGadget resolve(Scene scene, BlossomCampData camp, BlossomType kind) {
        var key = new CampKind(camp.getId(), kind.refreshTypeId());
        var cached = resolved.get(key);
        if (cached != null) return cached;
        for (int groupId : camp.getNewGroupVec()) {
            SceneGroup group = SceneGroup.of(groupId).load(scene.getId());
            if (group == null || group.gadgets == null) continue;
            var gadget = group.gadgets.values().stream()
                    .filter(item -> item.gadget_id == kind.getGadgetId())
                    .min(Comparator.comparingInt(item -> item.config_id)).orElse(null);
            if (gadget != null) {
                resolved.put(key, gadget);
                return gadget;
            }
        }
        return null;
    }

    boolean ownsGroup(int groupId) {
        var supported = BlossomType.all().stream()
                .map(BlossomType::refreshTypeId).collect(java.util.stream.Collectors.toSet());
        return GameData.getBlossomCampDataMap().values().stream()
                .filter(camp -> camp.getRefreshTypeVec() != null
                        && camp.getRefreshTypeVec().stream().anyMatch(supported::contains))
                .anyMatch(camp -> camp.getNewGroupVec() != null
                        && camp.getNewGroupVec().contains(groupId));
    }

    /** Each occupied slot is an independent saved run, even within one refresh ID. */
    boolean advance(Scene scene, Source source, Collection<Integer> unlockedAreas) {
        var rows = GameData.getBlossomCampDataMap();
        var camp = rows.get(source.campId());
        if (camp == null || camp.getNextCampIdVec() == null
                || camp.getNextCampIdVec().isEmpty()) return false;
        var kind = BlossomType.valueOf(source.gadget().gadget_id);
        if (kind == null) return false;
        var host = scene.getWorld().getHost();
        var occupied = new HashSet<Integer>();
        var otherLocations = new ArrayList<SceneGadget>();
        for (var refresh : GameData.getBlossomRefreshExcelConfigDataMap().values()) {
            if (refresh.getCityId() != camp.getCityId()
                    || BlossomType.forRefresh(refresh.getRefreshType()) == null
                    || !BlossomManager.refreshEligible(refresh, host.getLevel())) continue;
            for (int slot = 0; slot < refresh.getRefreshCount(); slot++) {
                if (refresh.getId() == source.refreshId() && slot == source.slot()) continue;
                if (host.isBlossomConsumed(refresh.getId(), slot)) continue;
                int id = host.getBlossomCampId(refresh.getId(), slot);
                if (id <= 0) continue;
                occupied.add(id);
                var targetCamp = rows.get(id);
                if (targetCamp == null) continue;
                var otherKind = BlossomType.forRefresh(refresh.getRefreshType());
                var gadget = resolve(scene, targetCamp, otherKind);
                if (gadget != null) otherLocations.add(gadget);
            }
        }
        int nextId = firstAvailableCamp(
                camp.getNextCampIdVec(), rows, camp.getCityId(), kind.refreshTypeId(), occupied,
                candidate -> {
                    var next = selectedCamp(scene, candidate, kind, source.refreshId(),
                            source.slot(), unlockedAreas);
                    return next != null && otherLocations.stream().noneMatch(other ->
                            other.pos.computeDistance(next.gadget().pos) < 2.5f);
                });
        if (nextId == 0) {
            Grasscutter.getLogger().warn(
                    "No unlocked ley-line successor city={} kind={} camp={} slot={}",
                    camp.getCityId(), kind.refreshType(), source.campId(), source.slot());
            return false;
        }
        host.advanceBlossomCampId(source.refreshId(), source.slot(), nextId);
        Grasscutter.getLogger().info(
                "Ley-line camp advanced city={} kind={} slot={} {} -> {}",
                camp.getCityId(), kind.refreshType(), source.slot(), source.campId(), nextId);
        return true;
    }
}
