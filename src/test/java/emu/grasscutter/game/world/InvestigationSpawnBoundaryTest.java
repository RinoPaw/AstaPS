package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

class InvestigationSpawnBoundaryTest {
    @Test
    void fallbackNeverSpawnsIntoNormallyLoadedOrQuestOwnedSuite() {
        assertFalse(InvestigationSpawnHelper.shouldForceSpawnGroup(
                133003002, Set.of(133003002, 133003079)));
        assertFalse(InvestigationSpawnHelper.shouldForceSpawnGroup(
                133003079, Set.of(133003002, 133003079)));
        assertTrue(InvestigationSpawnHelper.shouldForceSpawnGroup(
                133004195, Set.of(133003002, 133003079)));
    }
}
