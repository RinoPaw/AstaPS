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
    record SelectedCamp(int campId, int refreshId, int slot, SceneGadget gadget) {}
    private record CampGadgetKey(int campId, int nativeType) {}

    // A numeric tuple prevents ambiguity and never embeds a refresh slot in the Lua cache.
    private final Map<CampGadgetKey, SceneGadget> worktopCache = new HashMap<>();
    private volatile Set<Integer> blossomGroupIds;

    /**
     * Each camp is a node in its city's directed graph. Successors are
     * references to nodes, and occupancy belongs to this scene's graph.
     */
    static final class CampNode {
        final BlossomCampData data;
        final List<CampNode> successors = new ArrayList<>();
        SelectedCamp blossom;

        CampNode(BlossomCampData data) {
            this.data = data;
        }

        boolean supports(int nativeType) {
            return data.getRefreshTypeVec() != null
                    && data.getRefreshTypeVec().contains(nativeType)
                    && data.getNewGroupVec() != null
                    && !data.getNewGroupVec().isEmpty();
        }
    }

    static final class CityGraph {
        final Map<Integer, CampNode> nodes = new HashMap<>();

        CityGraph(int cityId, Collection<BlossomCampData> authored) {
            for (var data : authored)
                if (data.getCityId() == cityId)
                    nodes.put(data.getId(), new CampNode(data));
            for (var node : nodes.values()) {
                if (node.data.getNextCampIdVec() == null) continue;
                for (int nextId : node.data.getNextCampIdVec()) {
                    var successor = nodes.get(nextId);
                    if (successor != null) node.successors.add(successor);
                }
            }
        }

        void clearOccupancy() {
            nodes.values().forEach(node -> node.blossom = null);
        }

        /** The lowest camp ID of each section is its tentative start. */
        List<CampNode> sectionHeads(int nativeType) {
            var heads = new TreeMap<Integer, CampNode>();
            for (var node : nodes.values()) {
                if (!node.supports(nativeType)) continue;
                heads.merge(node.data.getSectionId(), node,
                        (a, b) -> a.data.getId() < b.data.getId() ? a : b);
            }
            return new ArrayList<>(heads.values());
        }

        /** Traverse original directed edges, including branches and cycles. */
        CampNode findAvailableCamp(
                Collection<CampNode> starts, int nativeType, Predicate<CampNode> usable) {
            var queue = new ArrayDeque<>(starts);
            var visited = new HashSet<Integer>();
            while (!queue.isEmpty()) {
                var node = queue.removeFirst();
                if (!visited.add(node.data.getId())) continue;
                queue.addAll(node.successors);
                if (node.blossom == null && node.supports(nativeType) && usable.test(node))
                    return node;
            }
            return null;
        }
    }

    private final Map<Integer, CityGraph> graphs = new HashMap<>();

    private CityGraph graphFor(int cityId) {
        return graphs.computeIfAbsent(cityId, city ->
                new CityGraph(city, GameData.getBlossomCampDataMap().values()));
    }

    private SelectedCamp resolveCampSelection(
            Scene scene, BlossomCampData camp, BlossomType kind, int refreshId, int slot,
            Collection<Integer> unlockedAreas) {
        if (camp.getRefreshTypeVec() == null
                || !camp.getRefreshTypeVec().contains(kind.refreshTypeId())) return null;
        var gadget = findWorktop(scene, camp, kind);
        // An unlocked region authorizes its camp; there is no city-wide shortcut.
        if (gadget == null || gadget.area_id <= 0
                || !unlockedAreas.contains(gadget.area_id)) return null;
        return new SelectedCamp(camp.getId(), refreshId, slot, gadget);
    }

    private SelectedCamp availableSelection(
            Scene scene, CityGraph graph, CampNode node, BlossomType kind,
            int refreshId, int slot, Collection<Integer> unlockedAreas) {
        if (node == null || node.blossom != null) return null;
        var selected = resolveCampSelection(
                scene, node.data, kind, refreshId, slot, unlockedAreas);
        if (selected == null) return null;
        for (var other : graph.nodes.values()) {
            if (other.blossom != null
                    && other.blossom.gadget().pos.computeDistance(selected.gadget().pos) < 2.5f)
                return null;
        }
        return selected;
    }

    private SelectedCamp findCamp(
            Scene scene, CityGraph graph, Collection<CampNode> starts, BlossomType kind,
            int refreshId, int slot, Collection<Integer> unlockedAreas) {
        var found = graph.findAvailableCamp(starts, kind.refreshTypeId(),
                node -> availableSelection(scene, graph, node, kind,
                        refreshId, slot, unlockedAreas) != null);
        return found == null ? null : availableSelection(
                scene, graph, found, kind, refreshId, slot, unlockedAreas);
    }

    private static void occupy(CityGraph graph, SelectedCamp selected) {
        graph.nodes.get(selected.campId()).blossom = selected;
    }

    /** Choose up to count different section heads after saved camps are reserved. */
    private void selectInitialCamps(
            Scene scene, CityGraph graph, BlossomRefreshExcelConfigData refresh,
            BlossomType kind, Collection<Integer> unlockedAreas,
            List<SelectedCamp> selected) {
        var host = scene.getWorld().getHost();
        var heads = graph.sectionHeads(kind.refreshTypeId());
        Collections.shuffle(heads);
        for (int slot = 0; slot < refresh.getRefreshCount(); slot++) {
            if (host.isBlossomConsumed(refresh.getId(), slot)) continue;
            final int currentSlot = slot;
            if (selected.stream().anyMatch(chosen ->
                    chosen.refreshId() == refresh.getId() && chosen.slot() == currentSlot))
                continue;

            while (!heads.isEmpty()) {
                var node = heads.remove(heads.size() - 1);
                var initial = availableSelection(scene, graph, node, kind,
                        refresh.getId(), slot, unlockedAreas);
                if (initial == null) continue;
                occupy(graph, initial);
                selected.add(initial);
                host.setBlossomCampId(refresh.getId(), slot, initial.campId());
                break;
            }
        }
    }

    /**
     * Reserve persisted camps before assigning new ones, so a random start
     * cannot displace a pending reward. Missing runs select different
     * section heads without replacement for each refresh configuration.
     */
    List<SelectedCamp> selectCamps(Scene scene, int rank, Collection<Integer> unlockedAreas) {
        var host = scene.getWorld().getHost();
        var refreshes = GameData.getBlossomRefreshExcelConfigDataMap().values().stream()
                .filter(refresh -> {
                    var kind = BlossomType.forRefresh(refresh.getRefreshType());
                    var city = GameData.getCityDataMap().get(refresh.getCityId());
                    return kind != null && refresh.getRefreshCount() > 0
                            && BlossomManager.refreshEligible(refresh, rank)
                            && city != null && city.getSceneId() == scene.getId();
                })
                .sorted(Comparator.comparingInt(BlossomRefreshExcelConfigData::getCityId)
                        .thenComparingInt(BlossomRefreshExcelConfigData::getId))
                .toList();

        // Reconstruct ownership from the host's saved runs whenever the
        // active scene needs a fresh selection.
        graphs.values().forEach(CityGraph::clearOccupancy);
        var selected = new ArrayList<SelectedCamp>();

        for (var refresh : refreshes) {
            var graph = graphFor(refresh.getCityId());
            var kind = BlossomType.forRefresh(refresh.getRefreshType());
            for (int slot = 0; slot < refresh.getRefreshCount(); slot++) {
                if (host.isBlossomConsumed(refresh.getId(), slot)) continue;
                int savedId = host.getBlossomCampId(refresh.getId(), slot);
                var savedNode = graph.nodes.get(savedId);
                if (savedNode == null) continue;
                var chosen = findCamp(scene, graph, List.of(savedNode), kind,
                        refresh.getId(), slot, unlockedAreas);
                if (chosen == null) continue;
                occupy(graph, chosen);
                selected.add(chosen);
                if (savedId != chosen.campId())
                    host.setBlossomCampId(refresh.getId(), slot, chosen.campId());
            }
        }

        for (var refresh : refreshes)
            selectInitialCamps(scene, graphFor(refresh.getCityId()), refresh,
                    BlossomType.forRefresh(refresh.getRefreshType()), unlockedAreas, selected);

        selected.sort(Comparator.comparingInt(SelectedCamp::refreshId)
                .thenComparingInt(SelectedCamp::slot));
        return selected;
    }

    /** Lua is the encounter authority; only the selected, authored worktop is needed here. */
    private SceneGadget findWorktop(Scene scene, BlossomCampData camp, BlossomType kind) {
        var key = new CampGadgetKey(camp.getId(), kind.refreshTypeId());
        var cached = worktopCache.get(key);
        if (cached != null) return cached;
        for (int groupId : camp.getNewGroupVec()) {
            SceneGroup group = SceneGroup.of(groupId).load(scene.getId());
            if (group == null || group.gadgets == null) continue;
            var gadget = group.gadgets.values().stream()
                    .filter(item -> item.gadget_id == kind.getGadgetId())
                    .min(Comparator.comparingInt(item -> item.config_id)).orElse(null);
            if (gadget != null) {
                worktopCache.put(key, gadget);
                return gadget;
            }
        }
        return null;
    }

    boolean containsGroup(int groupId) {
        var indexed = blossomGroupIds;
        if (indexed == null) {
            var supported = BlossomType.all().stream()
                    .map(BlossomType::refreshTypeId).collect(java.util.stream.Collectors.toSet());
            indexed = GameData.getBlossomCampDataMap().values().stream()
                    .filter(camp -> camp.getRefreshTypeVec() != null
                            && camp.getRefreshTypeVec().stream().anyMatch(supported::contains))
                    .filter(camp -> camp.getNewGroupVec() != null)
                    .flatMap(camp -> camp.getNewGroupVec().stream())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            // GameData is populated before live world scenes are created.
            blossomGroupIds = indexed;
        }
        return indexed.contains(groupId);
    }

    /** Move a claimed flower along authored successors, using graph occupancy. */
    boolean advanceCamp(Scene scene, SelectedCamp source, Collection<Integer> unlockedAreas) {
        var rows = GameData.getBlossomCampDataMap();
        var camp = rows.get(source.campId());
        if (camp == null) return false;
        var kind = BlossomType.valueOf(source.gadget().gadget_id);
        if (kind == null) return false;
        var graph = graphFor(camp.getCityId());
        var current = graph.nodes.get(source.campId());
        if (current == null || current.successors.isEmpty()) return false;

        // Authored cycles may lead back to the just-claimed camp.
        current.blossom = null;
        var next = findCamp(scene, graph, current.successors, kind,
                source.refreshId(), source.slot(), unlockedAreas);
        if (next == null) {
            current.blossom = source;
            Grasscutter.getLogger().warn(
                    "No unlocked ley-line successor city={} kind={} camp={} slot={}",
                    camp.getCityId(), kind.refreshType(), source.campId(), source.slot());
            return false;
        }

        scene.getWorld().getHost().advanceBlossomCampId(
                source.refreshId(), source.slot(), next.campId());
        occupy(graph, next);
        Grasscutter.getLogger().info(
                "Ley-line camp advanced city={} kind={} slot={} {} -> {}",
                camp.getCityId(), kind.refreshType(), source.slot(), source.campId(), next.campId());
        return true;
    }

}
