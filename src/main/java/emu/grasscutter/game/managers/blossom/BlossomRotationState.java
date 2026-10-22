package emu.grasscutter.game.managers.blossom;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** New player-save format. No legacy blossom-save translation. */
public final class BlossomRotationState {
    private long refreshDay = Long.MIN_VALUE;
    private Map<Integer, Integer> activeCamps = new HashMap<>();

    public synchronized void resetForDay(long day) {
        if (refreshDay == day) return;
        refreshDay = day;
        activeCamps.clear();
    }

    public synchronized int getCamp(int refreshId) {
        return activeCamps.getOrDefault(refreshId, 0);
    }

    public synchronized void setCamp(int refreshId, int campId) {
        activeCamps.put(refreshId, campId);
    }

    /** All native regular ley-line rows use a 04:00 local-server refresh. */
    public static long currentRefreshDay() {
        return LocalDateTime.now().minusHours(4).toLocalDate().toEpochDay();
    }
}
