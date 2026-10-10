package emu.grasscutter.game.dungeons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class DomainDropSafetyTest {
    private static DungeonDropEntry entry(List<Integer> counts, List<Integer> items) {
        var entry = new DungeonDropEntry();
        entry.setCounts(counts);
        entry.setItems(items);
        return entry;
    }

    @Test
    void acceptsUniformAndWeightedValidPools() {
        var first = entry(List.of(1, 3), List.of(104301, 104302));
        first.setProbabilities(List.of(1, 2, 1));
        first.setItemProbabilities(List.of(0, 10));
        var second = entry(List.of(2700), List.of(202));
        DomainDropSafety.validatePool(500, List.of(first, second));
    }

    @Test
    void rejectsEmptyItemPoolAndBrokenCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(5000, List.of(entry(List.of(5), List.of()))));
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(5000, List.of(entry(List.of(), List.of(202)))));
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(5000, List.of(entry(List.of(3, 2), List.of(202)))));
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(5000, List.of(entry(List.of(0, 10001), List.of(202)))));
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(5000, List.of(entry(List.of(1), List.of(-1)))));
    }

    @Test
    void rejectsBadWeightsBeforeRandomSelection() {
        var wrongLength = entry(List.of(1, 3), List.of(202));
        wrongLength.setProbabilities(List.of(50, 50));
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(5000, List.of(wrongLength)));

        var zeroMass = entry(List.of(1), List.of(202, 203));
        zeroMass.setItemProbabilities(List.of(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(5000, List.of(zeroMass)));

        var negative = entry(List.of(1), List.of(202, 203));
        negative.setItemProbabilities(List.of(-1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(5000, List.of(negative)));

        var overflow = entry(List.of(1), List.of(202, 203));
        overflow.setItemProbabilities(List.of(Integer.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(5000, List.of(overflow)));
    }

    @ParameterizedTest
    @ValueSource(ints = {5000, 5001, 5002, 5008, 5100, 5101})
    void knownBrokenDungeonPoolsAreRejected(int dungeonId) throws IOException {
        var source = Files.readString(Path.of("data", "DungeonDrop.json"));
        DungeonDrop[] drops = new Gson().fromJson(source, DungeonDrop[].class);
        Map<Integer, List<DungeonDropEntry>> byId = new HashMap<>();
        for (DungeonDrop drop : drops) {
            byId.put(drop.getDungeonId(), drop.getDrops());
        }
        assertTrue(byId.containsKey(dungeonId));
        assertThrows(IllegalArgumentException.class,
                () -> DomainDropSafety.validatePool(dungeonId, byId.get(dungeonId)));
    }

    @Test
    void emptyRewardsNeverChargeOrGrant() {
        var payments = new AtomicInteger();
        var grants = new AtomicInteger();
        var rewarded = new HashSet<Integer>();
        boolean success = DomainDropSafety.commitOnce(new Object(), rewarded, 101,
                new ArrayList<>(List.of(0, -1)), n -> n > 0,
                () -> { payments.incrementAndGet(); return true; },
                items -> grants.incrementAndGet());
        assertFalse(success);
        assertEquals(0, payments.get());
        assertEquals(0, grants.get());
        assertFalse(rewarded.contains(101));
    }

    @Test
    void failedPaymentLeavesRewardsUnclaimed() {
        var rewarded = new HashSet<Integer>();
        var grants = new AtomicInteger();
        boolean success = DomainDropSafety.commitOnce(new Object(), rewarded, 101,
                new ArrayList<>(List.of(202)), n -> n > 0,
                () -> false, items -> grants.incrementAndGet());
        assertFalse(success);
        assertEquals(0, grants.get());
        assertTrue(rewarded.isEmpty());
    }

    @Test
    void successfulClaimGrantsOnceAndRejectsDuplicatesBeforeCharging() {
        Object lock = new Object();
        var rewarded = new HashSet<Integer>();
        var payments = new AtomicInteger();
        var grants = new AtomicInteger();
        for (int attempt = 0; attempt < 2; attempt++) {
            boolean success = DomainDropSafety.commitOnce(lock, rewarded, 101,
                    new ArrayList<>(List.of(202)), n -> n > 0,
                    () -> { payments.incrementAndGet(); return true; },
                    items -> { assertEquals(List.of(202), items); grants.incrementAndGet(); });
            assertEquals(attempt == 0, success);
        }
        assertEquals(1, payments.get());
        assertEquals(1, grants.get());
        assertTrue(rewarded.contains(101));
    }
}
