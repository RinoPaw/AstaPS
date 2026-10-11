package emu.grasscutter.game.dungeons;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class DungeonRestartGuardTest {
    @Test
    void allowsClaimedRunsRegardlessOfRegionOrDungeonId() {
        // Tested client path: Fontaine weapon domain.
        assertTrue(DungeonRestartGuard.isEligible(4471, 40771, 40771,
                false, true, true, 1, 0));
        // Shared path: another region/scene must not need a hardcoded allowlist.
        assertTrue(DungeonRestartGuard.isEligible(4311, 40501, 40501,
                false, true, true, 1, 0));
        assertTrue(DungeonRestartGuard.isEligible(4651, 40763, 40763,
                false, true, true, 1, 0));
    }

    @Test
    void rejectsMissingClaimAndIncompleteRuns() {
        assertFalse(DungeonRestartGuard.isEligible(4471, 40771, 40771,
                false, true, false, 1, 0));
        assertFalse(DungeonRestartGuard.isEligible(4471, 40771, 40771,
                false, false, true, 1, 0));
    }

    @Test
    void rejectsMismatchedScenesAndMalformedRequests() {
        assertFalse(DungeonRestartGuard.isEligible(4471, 40771, 40770,
                false, true, true, 1, 0));
        assertFalse(DungeonRestartGuard.isEligible(0, 40771, 40771,
                false, true, true, 1, 0));
        assertFalse(DungeonRestartGuard.isEligible(4471, 0, 0,
                false, true, true, 1, 0));
        assertFalse(DungeonRestartGuard.isEligible(4471, 40771, 40771,
                false, true, true, 1, 3));
    }

    @Test
    void refusesToRestartTowerOrMoveOtherPartyMembersWithoutConsent() {
        assertFalse(DungeonRestartGuard.isEligible(4471, 40771, 40771,
                true, true, true, 1, 0));
        assertFalse(DungeonRestartGuard.isEligible(4471, 40771, 40771,
                false, true, true, 2, 0));
        assertFalse(DungeonRestartGuard.isEligible(4471, 40771, 40771,
                false, true, true, 0, 0));
    }
}
