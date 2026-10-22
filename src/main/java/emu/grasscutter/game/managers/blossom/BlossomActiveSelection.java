package emu.grasscutter.game.managers.blossom;

import emu.grasscutter.data.excels.BlossomGroupsExcelConfigData;
import emu.grasscutter.data.excels.BlossomRefreshExcelConfigData;
import emu.grasscutter.data.excels.BlossomSectionOrderExcelConfigData;
import emu.grasscutter.game.world.SpawnDataEntry;
import java.util.*;
import java.util.function.Predicate;

/**
 * A single authority for map markers and physically active outcrops.
 * Source group IDs are joined through BlossomGroups.newGroupVec. Never infer cities from
 * coordinates or from the unrelated imported spawn gadget's category.
 */
public final class BlossomActiveSelection {
    public record Active(
            SpawnDataEntry spawn, BlossomType type, int cityId, int refreshId, int campId) {}

    private record Option(SpawnDataEntry spawn, BlossomGroupsExcelConfigData group, int order) {}

    private BlossomActiveSelection() {}

    public static List<Active> select(
            int adventureRank,
            int playerUid,
            long refreshDay,
            List<SpawnDataEntry> candidates,
            Collection<BlossomGroupsExcelConfigData> groupRows,
            Collection<BlossomSectionOrderExcelConfigData> sectionRows,
            Collection<BlossomRefreshExcelConfigData> refreshRows,
            BlossomRotationState state,
            Predicate<BlossomRefreshExcelConfigData> eligible) {
        state.resetForDay(refreshDay);

        Map<Integer, Integer> sectionOrders = new HashMap<>();
        for (var section : sectionRows) {
            sectionOrders.put(section.getCityId() * 100000 + section.getSectionId(),
                    section.getOrder());
        }

        Map<Integer, BlossomGroupsExcelConfigData> groupsBySceneGroup = new HashMap<>();
        for (var group : groupRows) {
            if (group.getLimitLevel() > adventureRank || group.getNewGroupVec() == null
                    || group.getRefreshTypeVec() == null
                    || !group.getRefreshTypeVec().contains(1)
                    || !group.getRefreshTypeVec().contains(3)) continue;
            for (int groupId : group.getNewGroupVec()) {
                groupsBySceneGroup.putIfAbsent(groupId, group);
            }
        }

        Map<Integer, List<Option>> optionsByCity = new HashMap<>();
        for (var spawn : candidates) {
            if (spawn.getGroup() == null) continue;
            var group = groupsBySceneGroup.get(spawn.getGroup().getGroupId());
            if (group == null) continue;
            int order = sectionOrders.getOrDefault(
                    group.getCityId() * 100000 + group.getSectionId(), Integer.MAX_VALUE);
            optionsByCity.computeIfAbsent(group.getCityId(), ignored -> new ArrayList<>())
                    .add(new Option(spawn, group, order));
        }
        Comparator<Option> optionOrder = Comparator.comparingInt(Option::order)
                .thenComparingInt(option -> option.group().getId())
                .thenComparingInt(option -> option.spawn().getConfigId());
        optionsByCity.values().forEach(options -> options.sort(optionOrder));

        List<BlossomRefreshExcelConfigData> refreshes = refreshRows.stream()
                .filter(row -> "BLOSSOM_REFRESH_EXP".equals(row.getRefreshType())
                        || "BLOSSOM_REFRESH_SCOIN".equals(row.getRefreshType()))
                .filter(eligible)
                // Revelation takes precedence if both categories point at the same camp.
                .sorted(Comparator.comparingInt(BlossomRefreshExcelConfigData::getCityId)
                        .thenComparingInt(row ->
                                "BLOSSOM_REFRESH_EXP".equals(row.getRefreshType()) ? 0 : 1))
                .toList();

        List<Active> active = new ArrayList<>();
        Set<SpawnDataEntry> occupied = new HashSet<>();
        for (var refresh : refreshes) {
            var options = optionsByCity.getOrDefault(refresh.getCityId(), List.of());
            if (options.isEmpty()) continue;
            var type = "BLOSSOM_REFRESH_EXP".equals(refresh.getRefreshType())
                    ? BlossomType.BLUE : BlossomType.GOLD;
            int count = refresh.getRefreshCount();
            for (int slot = 0; slot < count; slot++) {
                int stateKey = refresh.getId() * 100 + slot;
                int preferred = state.getCamp(stateKey);
                Option selected = null;
                if (preferred != 0) {
                    for (var option : options) {
                        if (option.group().getId() == preferred && !occupied.contains(option.spawn())) {
                            selected = option;
                            break;
                        }
                    }
                }
                if (selected == null) {
                    int offset = (int) Math.floorMod(
                            (long) playerUid + refreshDay + refresh.getId() + slot,
                            (long) options.size());
                    for (int i = 0; i < options.size(); i++) {
                        var option = options.get((offset + i) % options.size());
                        if (!occupied.contains(option.spawn())) {
                            selected = option;
                            break;
                        }
                    }
                }
                if (selected == null) break;
                occupied.add(selected.spawn());
                state.setCamp(stateKey, selected.group().getId());
                active.add(new Active(selected.spawn(), type, refresh.getCityId(),
                        refresh.getId(), selected.group().getId()));
            }
        }
        return active;
    }

    public static void advance(
            Active claimed, BlossomRotationState state,
            Collection<BlossomGroupsExcelConfigData> groupRows) {
        var group = groupRows.stream().filter(row -> row.getId() == claimed.campId())
                .findFirst().orElse(null);
        int next = 0;
        if (group != null && group.getNextCampIdVec() != null
                && !group.getNextCampIdVec().isEmpty()) {
            next = group.getNextCampIdVec().get(0);
        }
        // The selector validates this preference against current eligible groups;
        // unresolvable links are replaced by the next deterministic candidate.
        state.setCamp(claimed.refreshId() * 100, next);
    }
}
