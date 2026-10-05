package emu.grasscutter.game.combine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReliquaryDecomposeSafetyTest {
    @Test
    void rejectsDuplicateGuid() {
        assertFalse(ReliquaryDecomposeSafety.validShape(1, List.of(10L, 10L, 11L)));
    }

    @Test
    void rejectsNonPositiveAndOversizedCounts() {
        assertFalse(ReliquaryDecomposeSafety.validShape(0, List.of()));
        assertFalse(ReliquaryDecomposeSafety.validShape(-1, List.of()));
        assertFalse(
                ReliquaryDecomposeSafety.validShape(
                        ReliquaryDecomposeSafety.MAX_RESULT_COUNT + 1, List.of()));
    }

    @Test
    void requiresExactlyThreeUniqueInputsPerResult() {
        assertFalse(ReliquaryDecomposeSafety.validShape(1, List.of(1L, 2L)));
        assertTrue(ReliquaryDecomposeSafety.validShape(1, List.of(1L, 2L, 3L)));
    }

    @Test
    void rejectsWrongTypeRankLockedAndEquippedInputs() {
        assertFalse(ReliquaryDecomposeSafety.eligible(false, 5, false, false));
        assertFalse(ReliquaryDecomposeSafety.eligible(true, 4, false, false));
        assertFalse(ReliquaryDecomposeSafety.eligible(true, 5, true, false));
        assertFalse(ReliquaryDecomposeSafety.eligible(true, 5, false, true));
        assertTrue(ReliquaryDecomposeSafety.eligible(true, 5, false, false));
    }
}
