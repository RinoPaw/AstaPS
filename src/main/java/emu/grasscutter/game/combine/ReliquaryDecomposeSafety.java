package emu.grasscutter.game.combine;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ReliquaryDecomposeSafety {
    static final int INPUTS_PER_RESULT = 3;
    static final int MAX_RESULT_COUNT = 1000;

    private ReliquaryDecomposeSafety() {}

    static boolean validShape(int resultCount, List<Long> guids) {
        if (resultCount <= 0 || resultCount > MAX_RESULT_COUNT || guids == null) {
            return false;
        }

        long expected = (long) resultCount * INPUTS_PER_RESULT;
        if (expected != guids.size()) {
            return false;
        }

        Set<Long> unique = new HashSet<>(guids.size());
        for (Long guid : guids) {
            if (guid == null || guid <= 0 || !unique.add(guid)) {
                return false;
            }
        }
        return true;
    }

    static boolean eligible(boolean reliquary, int rankLevel, boolean locked, boolean equipped) {
        return reliquary && rankLevel == 5 && !locked && !equipped;
    }
}
