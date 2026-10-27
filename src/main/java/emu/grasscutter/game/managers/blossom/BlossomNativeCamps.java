package emu.grasscutter.game.managers.blossom;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.BlossomCampData;
import emu.grasscutter.data.excels.BlossomRefreshExcelConfigData;
import emu.grasscutter.game.world.Scene;
import java.util.*;
import java.util.function.Predicate;

/** Directed city camp graphs and their active blossoms. */
final class BlossomNativeCamps {
    static final class Blossom {
        enum State { IDLE, FIGHTING, REWARD }

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
        final List<CampNode> next = new ArrayList<>();
        Blossom blossom;

        CampNode(int campId) { this.campId = campId; }
    }

    static final class CityGraph {
        final List<CampNode> nodes = new ArrayList<>();

        CityGraph(int cityId, Collection<BlossomCampData> authored) {
            var byId = new HashMap<Integer, CampNode>();
            var authoredById = new HashMap<Integer, BlossomCampData>();
            for (var data : authored) {
                if (data.getCityId() != cityId) continue;
                var node = new CampNode(data.getId());
                nodes.add(node);
                byId.put(data.getId(), node);
                authoredById.put(data.getId(), data);
            }
            for (var node : nodes) {
                var nextIds = authoredById.get(node.campId).getNextCampIdVec();
                if (nextIds == null) continue;
                for (int nextId : nextIds) {
                    var successor = byId.get(nextId);
                    if (successor != null) node.next.add(successor);
                }
            }
        }

        CampNode find(int campId) {
            return nodes.stream().filter(node -> node.campId == campId)
                    .findFirst().orElse(null);
        }

        /** Keep the original minimum-camp-ID head for each native section. */
        List<CampNode> sectionHeads(int nativeType, Collection<BlossomCampData> authored) {
            var sections = new TreeMap<Integer, CampNode>();
            for (var data : authored) {
                var node = find(data.getId());
                if (node == null || data.getRefreshTypeVec() == null
                        || !data.getRefreshTypeVec().contains(nativeType)
                        || data.getNewGroupVec() == null || data.getNewGroupVec().isEmpty())
                    continue;
                sections.merge(data.getSectionId(), node,
                        (a, b) -> a.campId < b.campId ? a : b);
            }
            return new ArrayList<>(sections.values());
        }

        /** Search the authored edges, including branches and cycles. */
        CampNode findAvailableCamp(Collection<CampNode> starts, Predicate<CampNode> usable) {
            var queue = new ArrayDeque<>(starts);
            var visited = new HashSet<Integer>();
            while (!queue.isEmpty()) {
                var node = queue.removeFirst();
                if (!visited.add(node.campId)) continue;
                queue.addAll(node.next);
                if (node.blossom == null && usable.test(node)) return node;
            }
            return null;
        }
    }

    private final BlossomManager manager;
    private final Map<Integer, CityGraph> graphs = new HashMap<>();
    private volatile Set<Integer> blossomGroupIds;
    private Map<RefreshKey, Integer> refreshIds;

    private record RefreshKey(int cityId, BlossomType type) {}

    /** Original refresh IDs are uniquely defined by city and flower type. */
    int refreshId(CampNode node) {
        if (refreshIds == null) {
            var indexed = new HashMap<RefreshKey, Integer>();
            for (var refresh : GameData.getBlossomRefreshExcelConfigDataMap().values()) {
                var type = BlossomType.forRefresh(refresh.getRefreshType());
                if (type == null) continue;
                var key = new RefreshKey(refresh.getCityId(), type);
                if (indexed.putIfAbsent(key, refresh.getId()) != null)
                    throw new IllegalStateException("Duplicate blossom refresh " + key);
            }
            refreshIds = Map.copyOf(indexed);
        }
        var camp = GameData.getBlossomCampDataMap().get(node.campId);
        if (camp == null) throw new IllegalStateException("Unknown blossom camp " + node.campId);
        var id = refreshIds.get(new RefreshKey(camp.getCityId(), node.blossom.type));
        if (id == null) throw new IllegalStateException("Missing blossom refresh for " + node.campId);
        return id;
    }

    BlossomNativeCamps(BlossomManager manager) {
        this.manager = manager;
    }

    private CityGraph graphFor(int cityId) {
        return graphs.computeIfAbsent(cityId, city ->
                new CityGraph(city, GameData.getBlossomCampDataMap().values()));
    }

    /** Area unlocking is required only when creating a new initial flower. */
    private CampNode availableSelection(
            CityGraph graph, CampNode node, BlossomType kind,
            int adventureRank, Collection<Integer> unlockedAreas, boolean initial) {
        if (node == null || node.blossom != null) return null;
        var camp = GameData.getBlossomCampDataMap().get(node.campId);
        if (camp == null || adventureRank < camp.getLimitLevel()) return null;
        var gadget = manager.resolveCampGadget(node.campId, kind, unlockedAreas, initial);
        if (gadget == null) return null;
        for (var other : graph.nodes) {
            if (other.blossom == null) continue;
            var otherGadget = manager.resolveCampGadget(
                    other.campId, other.blossom.type, unlockedAreas, false);
            if (otherGadget != null && otherGadget.pos.computeDistance(gadget.pos) < 2.5f)
                return null;
        }
        return node;
    }

    private CampNode findCamp(
            CityGraph graph, Collection<CampNode> starts, BlossomType kind,
            int adventureRank,
            Collection<Integer> unlockedAreas, boolean initial) {
        var found = graph.findAvailableCamp(starts,
                node -> availableSelection(graph, node, kind,
                        adventureRank, unlockedAreas, initial) != null);
        return found == null ? null : availableSelection(
                graph, found, kind, adventureRank, unlockedAreas, initial);
    }

    /** Add only missing slots; never move a surviving flower when eligibility changes. */
    private void selectInitialCamps(
            Scene scene, CityGraph graph, BlossomRefreshExcelConfigData refresh,
            BlossomType kind, Collection<Integer> unlockedAreas,
            List<CampNode> selected) {
        var host = scene.getWorld().getHost();
        var starts = graph.sectionHeads(kind.refreshTypeId(), GameData.getBlossomCampDataMap().values());
        Collections.shuffle(starts);
        for (int slot = 0; slot < refresh.getRefreshCount(); slot++) {
            if (host.isBlossomConsumed(refresh.getId(), slot)) continue;
            final int currentSlot = slot;
            if (selected.stream().anyMatch(current ->
                    refreshId(current) == refresh.getId() && current.blossom.slot == currentSlot))
                continue;
            while (!starts.isEmpty()) {
                var chosen = availableSelection(graph, starts.remove(starts.size() - 1),
                        kind, host.getLevel(), unlockedAreas, true);
                if (chosen == null) continue;
                chosen.blossom = new Blossom(kind, slot);
                selected.add(chosen);
                host.setBlossomCampId(refresh.getId(), slot, chosen.campId);
                break;
            }
        }
    }

    /** Reconcile persisted flowers and initialize only the missing slots. */
    List<CampNode> selectCamps(Scene scene, int rank, Collection<Integer> unlockedAreas) {
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
        var selected = new ArrayList<CampNode>();

        for (var refresh : refreshes) {
            var graph = graphFor(refresh.getCityId());
            var kind = BlossomType.forRefresh(refresh.getRefreshType());
            for (int slot = 0; slot < refresh.getRefreshCount(); slot++) {
                if (host.isBlossomConsumed(refresh.getId(), slot)) continue;
                int savedId = host.getBlossomCampId(refresh.getId(), slot);
                if (savedId == 0) continue;
                var node = graph.find(savedId);
                if (node == null) continue;
                var gadget = manager.resolveCampGadget(savedId, kind, unlockedAreas, false);
                if (gadget == null) continue;
                if (node.blossom == null) {
                    node.blossom = new Blossom(kind, slot);
                    if (host.isBlossomRewardReady(refresh.getId(), slot))
                        node.blossom.state = Blossom.State.REWARD;
                }
                if (node.blossom.type != kind || node.blossom.slot != slot
                        || refreshId(node) != refresh.getId()) continue;
                selected.add(node);
            }
        }

        for (var refresh : refreshes)
            selectInitialCamps(scene, graphFor(refresh.getCityId()), refresh,
                    BlossomType.forRefresh(refresh.getRefreshType()), unlockedAreas, selected);

        selected.sort(Comparator.comparingInt((CampNode node) -> refreshId(node))
                .thenComparingInt(node -> node.blossom.slot));
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
    boolean advanceCamp(Scene scene, CampNode source, Collection<Integer> unlockedAreas) {
        var camp = GameData.getBlossomCampDataMap().get(source.campId);
        if (camp == null) return false;
        var graph = graphFor(camp.getCityId());
        if (source.blossom == null || source.next.isEmpty()) {
            source.blossom = null;
            return false;
        }

        int refreshId = refreshId(source);
        var blossom = source.blossom;
        source.blossom = null;
        var successors = source.next;
        var chosen = findCamp(graph, successors, blossom.type,
                scene.getWorld().getHost().getLevel(), unlockedAreas, false);
        if (chosen == null) {
            Grasscutter.getLogger().warn(
                    "No valid ley-line successor city={} kind={} camp={} slot={}",
                    camp.getCityId(), blossom.type, source.campId, blossom.slot);
            return false;
        }

        blossom.state = Blossom.State.IDLE;
        chosen.blossom = blossom;
        scene.getWorld().getHost().advanceBlossomCampId(
                refreshId, blossom.slot, chosen.campId);
        Grasscutter.getLogger().info("Ley-line camp advanced city={} type={} slot={} {} -> {}",
                camp.getCityId(), blossom.type, blossom.slot, source.campId, chosen.campId);
        return true;
    }
}
