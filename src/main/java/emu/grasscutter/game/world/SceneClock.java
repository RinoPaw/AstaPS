package emu.grasscutter.game.world;

import java.util.function.LongSupplier;

/** Monotonic scene timer independent of the quest-controlled world/day-night clock. */
final class SceneClock {
    private final LongSupplier nanoTime;
    private long lastNanos;
    private long elapsedNanos;

    SceneClock() {
        this(System::nanoTime);
    }

    SceneClock(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
        this.lastNanos = nanoTime.getAsLong();
    }

    synchronized int elapsedMillis(boolean paused) {
        long now = nanoTime.getAsLong();
        if (!paused) elapsedNanos += now - lastNanos;
        lastNanos = now;
        return (int) (elapsedNanos / 1_000_000L);
    }
}