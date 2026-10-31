package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SceneClockTest {
    @Test
    void sceneTimeContinuesWithoutWorldDayNightTime() {
        var nanos = new AtomicLong();
        var clock = new SceneClock(nanos::get);

        assertEquals(0, clock.elapsedMillis(false));
        nanos.addAndGet(1_500_000_000L);
        assertEquals(1500, clock.elapsedMillis(false));
        nanos.addAndGet(2_000_000_000L);
        assertEquals(3500, clock.elapsedMillis(false));
    }

    @Test
    void pauseFreezesSceneTimeAndResumesWithoutCatchUp() {
        var nanos = new AtomicLong();
        var clock = new SceneClock(nanos::get);

        nanos.addAndGet(1_000_000_000L);
        assertEquals(1000, clock.elapsedMillis(false));
        assertEquals(1000, clock.elapsedMillis(true));
        nanos.addAndGet(30_000_000_000L);
        assertEquals(1000, clock.elapsedMillis(true));
        assertEquals(1000, clock.elapsedMillis(true));
        nanos.addAndGet(250_000_000L);
        assertEquals(1250, clock.elapsedMillis(false));
    }
}