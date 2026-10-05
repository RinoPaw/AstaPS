package emu.grasscutter.game.combine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class CombineSafetyTest {
    @Test
    void rejectsNonPositiveAndHugeQuantity() {
        assertNull(CombineSafety.plan(0, 1, List.of(new CombineSafety.Cost(1, 1))));
        assertNull(CombineSafety.plan(-1, 1, List.of(new CombineSafety.Cost(1, 1))));
        assertNull(
                CombineSafety.plan(
                        CombineSafety.MAX_QUANTITY + 1, 1, List.of(new CombineSafety.Cost(1, 1))));
    }

    @Test
    void rejectsCostAndOutputOverflow() {
        assertNull(CombineSafety.plan(2, 1, List.of(new CombineSafety.Cost(1, Integer.MAX_VALUE))));
        assertNull(CombineSafety.plan(2, Integer.MAX_VALUE, List.of(new CombineSafety.Cost(1, 1))));
    }

    @Test
    void aggregatesDuplicateCostsBeforeMutation() {
        var plan =
                CombineSafety.plan(
                        2,
                        1,
                        List.of(new CombineSafety.Cost(100, 3), new CombineSafety.Cost(100, 4)));
        assertNotNull(plan);
        assertEquals(List.of(new CombineSafety.Cost(100, 14)), plan.costs());
        assertEquals(2, plan.resultCount());
    }

    @Test
    void rejectsDuplicateCostAggregateOverflow() {
        assertNull(
                CombineSafety.plan(
                        1,
                        1,
                        List.of(
                                new CombineSafety.Cost(100, Integer.MAX_VALUE),
                                new CombineSafety.Cost(100, 1))));
    }
}
