package emu.grasscutter.game.managers.blossom;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.BlossomCampData;
import emu.grasscutter.data.excels.BlossomRefreshExcelConfigData;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.data.SceneGadget;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.constants.EventType;
import java.util.*;
import java.util.function.Predicate;

/**
 * Authoritative 7.1 ley-line camp locations and next-camp edges.
 * BlossomGroups.newGroupVec refers to the original Lua scene group, which has
 * separate gold/blue worktops at the same authored location.
 */
final class BlossomNativeCamps {
    /** A selected native camp uses the Lua gadget directly, without a synthetic SpawnDataEntry. */
    record Source(int campId, int refreshId, SceneGadget gadget) {}

    private final Map<String, Source> resolved = new HashMap<>();
    private final Set<Integer> unresolvedGroups = new HashSet<>();

    /** Provisional start in native camp-ID order; original starting rule is unverified. */
    static int initialId(List<BlossomCampData> eligible) {
        return eligible.isEmpty() ? 0 : eligible.get(0).getId();
    }

    // Both blossom types share the camp graph. Only real authored Lua locations qualify.
    static int firstAvailableCamp(
            Collection<Integer> starts, Map<Integer, BlossomCampData> camps,
            int city, int rank, int refreshType, Set<Integer> occupied,
            Predicate<BlossomCampData> usable) {
        var queue = new ArrayDeque<>(starts);
        var visited = new HashSet<Integer>();
        while (!queue.isEmpty()) {
            int id = queue.removeFirst();
            if (!visited.add(id)) continue;
            var camp = camps.get(id);
            if (camp == null || camp.getCityId() != city) continue;
            if (camp.getNextCampIdVec() != null) queue.addAll(camp.getNextCampIdVec());
            if (occupied.contains(id) || camp.getLimitLevel() > rank
                    || camp.getRefreshTypeVec() == null || !camp.getRefreshTypeVec().contains(refreshType)
                    || camp.getNewGroupVec() == null || camp.getNewGroupVec().isEmpty()) continue;
            if (usable.test(camp)) return id;
        }
        return 0;
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

    List<Source> select(Scene scene, int rank, Collection<Integer> unlockedAreas) {
        var rows = GameData.getBlossomCampDataMap();
        if (rows.isEmpty()) return List.of();
        var cities = GameData.getCityDataMap().values();
        var refreshes = new ArrayList<>(GameData.getBlossomRefreshExcelConfigDataMap().values());
        // Keep the two cursors for each country together, irrespective of refresh IDs.
        refreshes.sort(Comparator.comparingInt(BlossomRefreshExcelConfigData::getCityId)
                .thenComparingInt(BlossomRefreshExcelConfigData::getId));
        var selected = new ArrayList<Source>();
        var occupiedByCity = new HashMap<Integer, Set<Integer>>();
        var selectedByCity = new HashMap<Integer, List<Source>>();
        var campsByCity = new HashMap<Integer, List<BlossomCampData>>();
        for (var camp : rows.values()) {
            if (camp.getNewGroupVec() == null || camp.getNewGroupVec().isEmpty()
                    || camp.getRefreshTypeVec() == null || camp.getLimitLevel() > rank) continue;
            campsByCity.computeIfAbsent(camp.getCityId(), unused -> new ArrayList<>()).add(camp);
        }
        campsByCity.values().forEach(camps ->
                camps.sort(Comparator.comparingInt(BlossomCampData::getId)));
        var host = scene.getWorld().getHost();

        for (var refresh : refreshes) {
            BlossomType kind = switch (refresh.getRefreshType()) {
                case "BLOSSOM_REFRESH_SCOIN" -> BlossomType.GOLD;
                case "BLOSSOM_REFRESH_EXP" -> BlossomType.BLUE;
                default -> null;
            };
            if (kind == null || refresh.getRefreshCount() <= 0
                    || host.isBlossomConsumed(refresh.getId())
                    || !BlossomManager.refreshEligible(refresh, rank, unlockedAreas, cities)) continue;

            int city = refresh.getCityId();
            var cityData = GameData.getCityDataMap().get(city);
            if (cityData == null || cityData.getSceneId() != scene.getId()) continue;
            int refreshType = kind == BlossomType.GOLD ? 1 : 3;
            var eligible = campsByCity.getOrDefault(city, List.of()).stream()
                    .filter(camp -> camp.getRefreshTypeVec().contains(refreshType))
                    .toList();
            if (eligible.isEmpty()) continue;

            var occupied = occupiedByCity.computeIfAbsent(city, unused -> new HashSet<>());
            var local = selectedByCity.computeIfAbsent(city, unused -> new ArrayList<>());
            int savedId = host.getBlossomCampId(refresh.getId());
            int initial = initialId(eligible);
            int chosenId = firstAvailableCamp(
                    List.of(savedId > 0 ? savedId : initial), rows, city, rank, refreshType, occupied,
                    candidate -> {
                        var source = selectedCamp(scene, candidate.getId(), city, rank, kind, refresh.getId());
                        return source != null && local.stream().noneMatch(existing ->
                                existing.gadget().pos.computeDistance(source.gadget().pos) < 2.5f);
                    });
            if (chosenId == 0) continue;
            var source = selectedCamp(scene, chosenId, city, rank, kind, refresh.getId());
            selected.add(source);
            local.add(source);
            occupied.add(chosenId);
            if (savedId != chosenId) host.setBlossomCampId(refresh.getId(), chosenId);
        }
        return selected;
    }

    private Source resolve(Scene scene, BlossomCampData camp, BlossomType kind, int refreshId) {
        String key = camp.getId() + ":" + kind.name();
        Source existing = resolved.get(key);
        if (existing != null) return existing;
        for (int groupId : camp.getNewGroupVec()) {
            var group = SceneGroup.of(groupId).load(scene.getId());
            if (!hasNativeEncounter(group, kind)) {
                if (unresolvedGroups.add(groupId)) {
                    Grasscutter.getLogger().warn(
                            "Ley-line group missing original encounter chain scene={} group={}",
                            scene.getId(), groupId);
                }
                continue;
            }
            var gadget = group.gadgets.values().stream()
                    .filter(item -> item.gadget_id == kind.getGadgetId())
                    .min(Comparator.comparingInt(item -> item.config_id))
                    .orElse(null);
            if (gadget == null) continue;
            Source result = new Source(camp.getId(), refreshId, gadget);
            resolved.put(key, result);
            return result;
        }
        return null;
    }

    /** Camp ownership comes from BlossomGroups, never from a Gadget prefab ID. */
    boolean ownsGroup(int groupId) {
        return GameData.getBlossomCampDataMap().values().stream()
                .filter(camp -> camp.getRefreshTypeVec() != null
                        && camp.getRefreshTypeVec().contains(1)
                        && camp.getRefreshTypeVec().contains(3))
                .anyMatch(camp -> camp.getNewGroupVec() != null
                        && camp.getNewGroupVec().contains(groupId));
    }

    /**
     * A blossom worktop must have a runnable original encounter. An authored Gadget
     * without SELECT_OPTION / progress / reward triggers is insufficient.
     */
    static boolean hasNativeEncounter(SceneGroup group, BlossomType kind) {
        if (group == null || kind == null || group.init_config == null
                || group.gadgets == null || group.monsters == null || group.monsters.isEmpty()
                || group.suites == null || group.suites.isEmpty()
                || group.triggers == null || group.triggers.isEmpty()) return false;
        if (group.gadgets.values().stream().noneMatch(g -> g.gadget_id == kind.getGadgetId())
                || group.gadgets.values().stream().noneMatch(g -> g.is_blossom_chest)) return false;
        var initial = group.getSuiteByIndex(group.init_config.suite);
        if (initial == null || initial.sceneTriggers == null
                || initial.sceneTriggers.stream().noneMatch(t -> t.getEvent() == EventType.EVENT_GROUP_LOAD))
            return false;
        var events = group.suites.stream()
                .filter(suite -> suite.sceneTriggers != null)
                .flatMap(suite -> suite.sceneTriggers.stream())
                .filter(trigger -> trigger.getAction() != null && !trigger.getAction().isEmpty())
                .map(emu.grasscutter.scripts.data.SceneTrigger::getEvent)
                .collect(java.util.stream.Collectors.toSet());
        return events.containsAll(Set.of(
                EventType.EVENT_GROUP_LOAD, EventType.EVENT_GROUP_REFRESH,
                EventType.EVENT_GADGET_CREATE, EventType.EVENT_SELECT_OPTION,
                EventType.EVENT_ANY_MONSTER_DIE, EventType.EVENT_BLOSSOM_PROGRESS_FINISH,
                EventType.EVENT_BLOSSOM_CHEST_DIE));
    }

    /** Advance only after a successful reward claim; the saved cursor survives scene reload. */
    boolean advance(Scene scene, Source source, int rank) {
        var camp = GameData.getBlossomCampDataMap().get(source.campId());
        if (camp == null || camp.getNextCampIdVec() == null || camp.getNextCampIdVec().isEmpty())
            return false;
        var kind = BlossomType.valueOf(source.gadget().gadget_id);
        if (kind == null) return false;
        var host = scene.getWorld().getHost();
        var occupied = new HashSet<Integer>();
        for (var refresh : GameData.getBlossomRefreshExcelConfigDataMap().values()) {
            if (refresh.getCityId() != camp.getCityId() || refresh.getId() == source.refreshId())
                continue;
            if (!"BLOSSOM_REFRESH_SCOIN".equals(refresh.getRefreshType())
                    && !"BLOSSOM_REFRESH_EXP".equals(refresh.getRefreshType())) continue;
            int otherCampId = host.getBlossomCampId(refresh.getId());
            if (otherCampId > 0) occupied.add(otherCampId);
        }
        int nextId = firstAvailableCamp(
                camp.getNextCampIdVec(), GameData.getBlossomCampDataMap(),
                camp.getCityId(), rank, kind == BlossomType.GOLD ? 1 : 3, occupied,
                candidate -> selectedCamp(
                        scene, candidate.getId(), camp.getCityId(), rank, kind, source.refreshId()) != null);
        if (nextId == 0) {
            Grasscutter.getLogger().warn(
                    "No unoccupied authored ley-line successor city={} kind={} camp={}",
                    camp.getCityId(), kind, source.campId());
            return false;
        }
        host.advanceBlossomCampId(source.refreshId(), nextId);
        Grasscutter.getLogger().info("Ley-line camp advanced city={} kind={} {} -> {}",
                camp.getCityId(), kind, source.campId(), nextId);
        return true;
    }
}
