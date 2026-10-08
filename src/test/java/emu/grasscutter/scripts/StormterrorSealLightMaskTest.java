package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class StormterrorSealLightMaskTest {
    @Test
    void allThreeDistinctLightsMakeMaskSeven() {
        int mask = 0;
        mask = ScriptLib.recordSealLightReceipt(mask, 1);
        mask = ScriptLib.recordSealLightReceipt(mask, 2);
        mask = ScriptLib.recordSealLightReceipt(mask, 4);
        assertEquals(7, mask);
    }

    @Test
    void duplicateLightDoesNotIncrementMask() {
        assertEquals(3, ScriptLib.recordSealLightReceipt(3, 1));
        assertEquals(3, ScriptLib.recordSealLightReceipt(3, 2));
        assertEquals(7, ScriptLib.recordSealLightReceipt(3, 4));
    }

    @Test
    void specialHandlingIsLimitedToStormterrorSealGroups() {
        for (int id : new int[] {133007228, 133007229, 133007230}) {
            assertTrue(ScriptLib.isSealLightReceipt(id, "Temp_Point_Value", 1));
            assertTrue(ScriptLib.isSealLightReceipt(id, "Temp_Point_Value", 2));
            assertTrue(ScriptLib.isSealLightReceipt(id, "Temp_Point_Value", 4));
        }
        assertFalse(ScriptLib.isSealLightReceipt(133007227, "Temp_Point_Value", 1));
        assertFalse(ScriptLib.isSealLightReceipt(133007231, "Temp_Point_Value", 1));
        assertFalse(ScriptLib.isSealLightReceipt(133007228, "Point_Value", 1));
        assertFalse(ScriptLib.isSealLightReceipt(133007228, "Temp_Point_Value", -1));
    }
}
