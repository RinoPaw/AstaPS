package emu.grasscutter.game.managers.blossom;

import emu.grasscutter.data.excels.BlossomCampData;
import java.util.*;
import java.util.function.Predicate;

/** The native per-city camp graph, including section starts and directed successors. */
final class BlossomGraph {
    static final class CampNode {
        final int campId;
        final List<CampNode> next = new ArrayList<>();
        BlossomManager.Blossom blossom;

        CampNode(int campId) { this.campId = campId; }
    }

    static final class CityGraph {
        final Map<Integer, CampNode> nodes = new HashMap<>();
        private final Map<BlossomType, List<CampNode>> initialCandidates = new EnumMap<>(BlossomType.class);

        CityGraph(int cityId, Collection<BlossomCampData> authored) {
            var sections = new EnumMap<BlossomType, Map<Integer, CampNode>>(BlossomType.class);

            for (var data : authored) {
                if (data.getCityId() != cityId) continue;
                var node = new CampNode(data.getId());
                nodes.put(data.getId(), node);

                if (data.getRefreshTypeVec() == null) continue;
                for (var type : BlossomType.values()) {
                    if (!data.getRefreshTypeVec().contains(type.refreshTypeId())) continue;
                    sections.computeIfAbsent(type, unused -> new HashMap<>())
                            .merge(data.getSectionId(), node,
                                    (a, b) -> a.campId < b.campId ? a : b);
                }
            }

            for (var data : authored) {
                if (data.getCityId() != cityId) continue;
                var node = nodes.get(data.getId());
                var nextIds = data.getNextCampIdVec();
                if (nextIds == null) continue;
                for (int nextId : nextIds) {
                    var successor = nodes.get(nextId);
                    if (successor != null) node.next.add(successor);
                }
            }
            sections.forEach((type, heads) -> initialCandidates.put(type, List.copyOf(heads.values())));
        }

        /** The per-city initial candidates are selected when the graph is built. */
        List<CampNode> sectionHeads(BlossomType type) {
            return new ArrayList<>(initialCandidates.getOrDefault(type, List.of()));
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


}
