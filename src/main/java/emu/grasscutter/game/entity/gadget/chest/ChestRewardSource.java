package emu.grasscutter.game.entity.gadget.chest;

import java.util.function.BiPredicate;

/** Chooses the authoritatively configured chest drop without fabricating fallback rewards. */
public final class ChestRewardSource {
    private ChestRewardSource() {}

    /** An explicit chest drop ID takes precedence over a generic tag, even when unavailable. */
    public static boolean grant(
            int chestDropId,
            int dropCount,
            String dropTag,
            int level,
            BiPredicate<Integer, Integer> byId,
            BiPredicate<String, Integer> byTag) {
        if (chestDropId > 0) {
            return byId.test(chestDropId, Math.max(1, dropCount));
        }
        if (dropTag != null && !dropTag.isBlank()) {
            return byTag.test(dropTag, Math.max(1, level));
        }
        return false;
    }
}
