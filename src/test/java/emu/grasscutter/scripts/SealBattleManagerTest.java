package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.scripts.constants.SealBattleType;
import org.junit.jupiter.api.Test;

final class SealBattleManagerTest {
    @Test
    void supportedKillMonsterSealHasFinitePositiveGoalTimeAndRadius() {
        int type = SealBattleType.KILL_MONSTER.ordinal();
        assertTrue(SealBattleManager.validBattleParameters(type, 3, 60, 30.0));
        assertFalse(SealBattleManager.validBattleParameters(type, 0, 60, 30.0));
        assertFalse(SealBattleManager.validBattleParameters(type, 3, 0, 30.0));
        assertFalse(SealBattleManager.validBattleParameters(type, 3, 60, 0.0));
        assertFalse(SealBattleManager.validBattleParameters(type, 3, 60, Double.NaN));
        assertFalse(SealBattleManager.validBattleParameters(type, 3, 60, Double.POSITIVE_INFINITY));
    }

    @Test
    void unimplementedSealBattleModesAreRejectedExplicitly() {
        assertFalse(SealBattleManager.validBattleParameters(
                SealBattleType.ENERGY_CHARGE.ordinal(), 3, 60, 30));
        assertFalse(SealBattleManager.validBattleParameters(
                SealBattleType.NONE.ordinal(), 3, 60, 30));
    }
}
