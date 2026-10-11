package emu.grasscutter.game.dungeons;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FontaineDomainRetryGuardTest {
    @Test
    void allowsEmptyRetryAfterClaimForFourFontaineWeaponDungeons() {
        for (int dungeonId = 4470; dungeonId <= 4473; dungeonId++) {
            assertTrue(FontaineDomainRetryGuard.isEligible(
                    dungeonId, 40770 + dungeonId - 4470, true, true, 0));
        }
    }

    @Test
    void rejectsRetryBeforeRewardClaimOrCompletion() {
        assertFalse(FontaineDomainRetryGuard.isEligible(4471, 40771, true, false, 0));
        assertFalse(FontaineDomainRetryGuard.isEligible(4471, 40771, false, true, 0));
    }

    @Test
    void rejectsOtherScenesDungeonsAndNonemptyRequests() {
        assertFalse(FontaineDomainRetryGuard.isEligible(4471, 40770, true, true, 0));
        assertFalse(FontaineDomainRetryGuard.isEligible(4434, 40771, true, true, 0));
        assertFalse(FontaineDomainRetryGuard.isEligible(4471, 40771, true, true, 3));
    }
}
