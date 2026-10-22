package emu.grasscutter.game.managers.blossom;

import emu.grasscutter.data.excels.BlossomGroupsExcelConfigData;
import emu.grasscutter.data.excels.BlossomRefreshExcelConfigData;
import emu.grasscutter.game.world.SpawnDataEntry;
import java.util.*;
import java.util.function.*;

/** Selects the live outcrops. Both the map packet and scene spawning consume this result. */
final class BlossomSelection {
    static final float OVERLAP_RANGE = 2.5f;

    record Active(int refreshId, SpawnDataEntry spawn) {}

    private BlossomSelection() {}

    /** Original group IDs and original area outlines must agree on a city. */
    static int cityForGroup(
            int groupId, int areaCity, Collection<BlossomGroupsExcelConfigData> groups) {
        if (areaCity == 0) return 0;
        int city = 0;
        for (var group : groups) {
            if (group.getNewGroupVec() == null || !group.getNewGroupVec().contains(groupId)) continue;
            if (city != 0 && city != group.getCityId()) return 0;
            city = group.getCityId();
        }
        return city == areaCity ? city : 0;
    }

    static List<Active> select(
            List<SpawnDataEntry> sources,
            Collection<BlossomRefreshExcelConfigData> refreshRows,
            ToIntFunction<SpawnDataEntry> cityAt,
            Predicate<BlossomRefreshExcelConfigData> unlocked,
            ToLongFunction<BlossomRefreshExcelConfigData> refreshDay,
            Map<Integer, Integer> rotations,
            int uid,
            Collection<SpawnDataEntry> consumed) {
        var selected = new ArrayList<Active>();
        for (var row : refreshRows.stream()
                .sorted(Comparator.comparingInt(BlossomRefreshExcelConfigData::getId))
                .toList()) {
            BlossomType type = switch (row.getRefreshType() == null ? "" : row.getRefreshType()) {
                case "BLOSSOM_REFRESH_SCOIN" -> BlossomType.GOLD;
                case "BLOSSOM_REFRESH_EXP" -> BlossomType.BLUE;
                default -> null;
            };
            if (type == null || row.getRefreshCount() <= 0 || !unlocked.test(row)) continue;
            var available = sources.stream()
                    .filter(s -> s.getGadgetId() == type.getGadgetId())
                    .filter(s -> cityAt.applyAsInt(s) == row.getCityId())
                    .filter(s -> !consumed.contains(s))
                    .sorted(Comparator.comparingInt((SpawnDataEntry s) -> s.getGroup().getGroupId())
                            .thenComparingInt(SpawnDataEntry::getConfigId))
                    .toList();
            if (available.isEmpty()) continue;
            long seed = refreshDay.applyAsLong(row) * 1103515245L
                    + (long) uid * 31 + row.getId() + rotations.getOrDefault(row.getId(), 0);
            int first = (int) Math.floorMod(seed, available.size());
            int count = 0;
            for (int i = 0; i < available.size() && count < row.getRefreshCount(); i++) {
                var source = available.get((first + i) % available.size());
                if (selected.stream().anyMatch(existing ->
                        existing.spawn().getPos().computeDistance(source.getPos()) <= OVERLAP_RANGE)) {
                    continue;
                }
                selected.add(new Active(row.getId(), source));
                count++;
            }
        }
        return selected;
    }
}
