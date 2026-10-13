package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DomainHandbookHelperTest {
    @Test
    void handbookEntriesDoNotUnlockFreshPlayers() {
        var unlocked = new HashSet<Integer>();

        assertTrue(
                DomainHandbookHelper.alreadyUnlockedEntries(List.of(48, 44, 131), unlocked)
                        .isEmpty());
        assertTrue(unlocked.isEmpty());
    }

    @Test
    void handbookSyncIncludesOnlyPreviouslyUnlockedEntriesWithoutChangingState() {
        var unlocked = new HashSet<>(Set.of(44, 100));

        var visible = DomainHandbookHelper.alreadyUnlockedEntries(
                List.of(48, 44, 131, 100), unlocked);

        assertEquals(List.of(44, 100), visible);
        assertEquals(Set.of(44, 100), unlocked);
    }
}
