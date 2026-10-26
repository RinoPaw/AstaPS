package emu.grasscutter.game.managers.blossom;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.BlossomCampData;
import emu.grasscutter.data.excels.BlossomRefreshExcelConfigData;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.data.SceneGadget;
import java.util.*;
import java.util.function.Predicate;

/** Directed city camp graphs and their active blossoms. */
final class BlossomNativeCamps {
    /** Derived Lua view; the graph only owns the blossom and camp ID. */
    record ActiveCamp(CampNode node, int refreshId, SceneGadget gadget) {
        int campId() { return node.campId; }
        int slot() { return node.blossom.slot; }
        Blossom blossom() { return node.blossom; }
    }

    static final class Blossom {
        enum State { IDLE, FIGHTING, REWARD_READY, CLAIMABLE, CONSUMED }

        final BlossomType type;
        final int slot;
        State state = State.IDLE;

        Blossom(BlossomType type, int slot) {
            this.type = type;
            this.slot = slot;
        }
    }

    static final class CampNode {
        final int campId;
        final List<Integer> nextCampIds = new ArrayList<>();
        Blossom blossom;

        CampNode(int campId) { this.campId = campId; }
    }

    static final class CityGraph {
        final Map<Integer, CampNode> nodes = new HashMap<>();
        private final Map<Integer, List<CampNode>> headsByType = new HashMap<>();

        CityGraph(int cityId, Collection<BlossomCampData> authored) {
            var rows = new ArrayList<BlossomCampData>();
            for (var data : authored) {
                if (data.getCityId() != cityId) continue;
                rows.add(data);
                nodes.put(data.getId(), new CampNode(data.getId()));
            }

            var sections = new HashMap<Integer, TreeMap<Integer, CampNode>>();
            for (var data : rows) {
                var node = nodes.get(data.getId());
                if (data.getNextCampIdVec() != null)
                    for (int nextId : data.getNextCampIdVec())
                        if (nodes.containsKey(nextId)) node.nextCampIds.add(nextId);
                if (data.getRefreshTypeVec() == null || data.getNewGroupVec() == null
                        || data.getNewGroupVec().isEmpty()) continue;
                for (int type : data.getRefreshTypeVec())
                    sections.computeIfAbsent(type, unused -> new TreeMap<>())
                            .merge(data.getSectionId(), node,
                                    (a, b) -> a.campId < b.campId ? a : b);
            }
            sections.forEach((type, starts) ->
                    headsByType.put(type, new ArrayList<>(starts.values())));
        }

        List<CampNode> sectionHeads(int nativeType) {
            return new ArrayList<>(headsByType.getOrDefault(nativeType, List.of()));
        }

        /** Search the authored edges, including branches and cycles. */
        CampNode findAvailableCamp(Collection<CampNode> starts, Predicate<CampNode> usable) {
            var queue = new ArrayDeque<>(starts);
            var visited = new HashSet<Integer>();
            while (!queue.isEmpty()) {
                var node = queue.removeFirst();
                if (!visited.add(node.campId)) continue;
                for (int id : node.nextCampIds) queue.add(nodes.get(id));
                if (node.blossom == null && usable.test(node)) return node;
            }
            return null;
        }
    }

    private final BlossomManager manager;
    private final Map<Integer, CityGraph> graphs = new HashMap<>();
    private volatile Set<Integer> blossomGroupIds;

    BlossomNativeCamps(BlossomManager manager) {
        this.manager = manager;
    }

    private CityGraph graphFor(int cityId) {
        return graphs.computeIfAbsent(cityId, city ->
                new CityGraph(city, GameData.getBlossomCampDataMap().values()));
    }

    /** Area unlocking is required only when creating a new initial flower. */
    private ActiveCamp availableSelection(
            CityGraph graph, CampNode node, BlossomType kind, int refreshId, int slot,
            Collection<Integer> unlockedAreas, boolean initial) {
        if (node == null || node.blossom != null) return null;
        var gadget = manager.resolveCampGadget(node.campId, kind, unlockedAreas, initial);
        if (gadget == null) return null;
        for (var other : graph.nodes.values()) {
            if (other.blossom == null) continue;
            var otherGadget = manager.resolveCampGadget(
                    other.campId, other.blossom.type, unlockedAreas, false);
            if (otherGadget != null && otherGadget.pos.computeDistance(gadget.pos) < 2.5f)
                return null;
        }
        // The caller occupies the node after checking all candidates.
        return new ActiveCamp(node, refreshId, gadget);
    }

    private ActiveCamp findCamp(
            CityGraph graph, Collection<CampNode> starts, BlossomType kind,
            int refreshId, int slot, Collection<Integer> unlockedAreas, boolean initial) {
        var found = graph.findAvailableCamp(starts,
                node -> availableSelection(graph, node, kind,
                        refreshId, slot, unlockedAreas, initial) != null);
        return found == null ? null : availableSelection(
                graph, found, kind, refreshId, slot, unlockedAreas, initial);
    }

    private static void occupy(ActiveCamp chosen, Blossom blossom) {
        chosen.node().blossom = blossom;
    }

    /** Add only missing slots; never move a surviving flower when eligibility changes. */
    private void selectInitialCamps(
            Scene scene, CityGraph graph, BlossomRefreshExcelConfigData refresh,
            BlossomType kind, Collection<Integer> unlockedAreas,
            List<ActiveCamp> selected) {
        var host = scene.getWorld().getHost();
        var starts = graph.sectionHeads(kind.refreshTypeId());
        Collections.shuffle(starts);
        for (int slot = 0; slot < refresh.getRefreshCount(); slot++) {
            if (host.isBlossomConsumed(refresh.getId(), slot)) continue;
            final int currentSlot = slot;
            if (selected.stream().anyMatch(current ->
                    current.refreshId() == refresh.getId() && current.slot() == currentSlot))
                continue;
            while (!starts.isEmpty()) {
                var chosen = availableSelection(graph, starts.remove(starts.size() - 1),
                        kind, refresh.getId(), slot, unlockedAreas, true);
                if (chosen == null) continue;
                occupy(chosen, new Blossom(kind, slot));
                selected.add(chosen);
                host.setBlossomCampId(refresh.getId(), slot, chosen.campId());
                break;
            }
        }
    }

    /** Reconcile persisted flowers and initialize only the missing slots. */
    List<ActiveCamp> selectCamps(Scene scene, int rank, Collection<Integer> unlockedAreas) {
        var host = scene.getWorld().getHost();
        var refreshes = GameData.getBlossomRefreshExcelConfigDataMap().values().stream()
                .filter(refresh -> {
                    var type = BlossomType.forRefresh(refresh.getRefreshType());
                    var city = GameData.getCityDataMap().get(refresh.getCityId());
                    return type != null && refresh.getRefreshCount() > 0
                            && BlossomManager.refreshEligible(refresh, rank)
                            && city != null && city.getSceneId() == scene.getId();
                })
                .sorted(Comparator.comparingInt(BlossomRefreshExcelConfigData::getCityId)
                        .thenComparingInt(BlossomRefreshExcelConfigData::getId))
                .toList();
        var selected = new ArrayList<ActiveCamp>();

        for (var refresh : refreshes) {
            var graph = graphFor(refresh.getCityId());
            var kind = BlossomType.forRefresh(refresh.getRefreshType());
            for (int slot = 0; slot < refresh.getRefreshCount(); slot++) {
                if (host.isBlossomConsumed(refresh.getId(), slot)) continue;
                int savedId = host.getBlossomCampId(refresh.getId(), slot);
                if (savedId == 0) continue;
                var node = graph.nodes.get(savedId);
                if (node == null) continue;
                var gadget = manager.resolveCampGadget(savedId, kind, unlockedAreas, false);
                if (gadget == null) continue;
                if (node.blossom == null) {
                    node.blossom = new Blossom(kind, slot);
                    if (host.isBlossomRewardReady(refresh.getId(), slot))
                        node.blossom.state = Blossom.State.REWARD_READY;
                }
                if (node.blossom.type != kind || node.blossom.slot != slot) continue;
                selected.add(new ActiveCamp(node, refresh.getId(), gadget));
            }
        }

        for (var refresh : refreshes)
            selectInitialCamps(scene, graphFor(refresh.getCityId()), refresh,
                    BlossomType.forRefresh(refresh.getRefreshType()), unlockedAreas, selected);

        selected.sort(Comparator.comparingInt(ActiveCamp::refreshId)
                .thenComparingInt(ActiveCamp::slot));
        return selected;
    }

    boolean containsGroup(int groupId) {
        var indexed = blossomGroupIds;
        if (indexed == null) {
            var types = BlossomType.all().stream()
                    .map(BlossomType::refreshTypeId).collect(java.util.stream.Collectors.toSet());
            indexed = GameData.getBlossomCampDataMap().values().stream()
                    .filter(camp -> camp.getRefreshTypeVec() != null
                            && camp.getRefreshTypeVec().stream().anyMatch(types::contains))
                    .filter(camp -> camp.getNewGroupVec() != null)
                    .flatMap(camp -> camp.getNewGroupVec().stream())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            blossomGroupIds = indexed;
        }
        return indexed.contains(groupId);
    }

    /** Move an existing flower; successors do not have to be in unlocked regions. */
    boolean advanceCamp(Scene scene, ActiveCamp source, Collection<Integer> unlockedAreas) {
        var camp = GameData.getBlossomCampDataMap().get(source.campId());
        if (camp == null) return false;
        var graph = graphFor(camp.getCityId());
        var current = graph.nodes.get(source.campId());
        if (current == null || current.blossom == null || current.nextCampIds.isEmpty()) {
            if (current != null) {
                if (current.blossom != null) current.blossom.state = Blossom.State.CONSUMED;
                current.blossom = null;
            }
            return false;
        }

        var blossom = current.blossom;
        current.blossom = null;
        var successors = current.nextCampIds.stream().map(graph.nodes::get).toList();
        var chosen = findCamp(graph, successors, blossom.type, source.refreshId(),
                blossom.slot, unlockedAreas, false);
        if (chosen == null) {
            blossom.state = Blossom.State.CONSUMED;
            Grasscutter.getLogger().warn(
                    "No valid ley-line successor city={} kind={} camp={} slot={}",
                    camp.getCityId(), blossom.type, source.campId(), blossom.slot);
            return false;
        }

        blossom.state = Blossom.State.IDLE;
        occupy(chosen, blossom);
        scene.getWorld().getHost().advanceBlossomCampId(
                source.refreshId(), blossom.slot, chosen.campId());
        Grasscutter.getLogger().info("Ley-line camp advanced city={} type={} slot={} {} -> {}",
                camp.getCityId(), blossom.type, blossom.slot, source.campId(), chosen.campId());
        return true;
    }
}
