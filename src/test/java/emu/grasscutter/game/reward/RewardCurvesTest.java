package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RewardCurvesTest {
    @Test
    void lookupReturnsExactCountsWithoutInterpolation() {
        var curves = RewardCurves.parse("""
                material:
                  "30~34": "1+1:52.18"
                  "35~39": "1+1:60.8"
                  "40": "2"
                  "50~": "2+1:3.87"
                """);
        var material = curves.require("material");
        assertEquals("1+1:52.18", material.lookup("30"));
        assertEquals("1+1:52.18", material.lookup("34"));
        assertEquals("1+1:60.8", material.lookup("35"));
        assertEquals("1+1:60.8", material.lookup("39"));
        assertEquals("2", material.lookup("40"));
        assertEquals("2+1:3.87", material.lookup("50"));
        assertEquals("2+1:3.87", material.lookup("120"));
        assertThrows(IllegalArgumentException.class, () -> material.lookup("41"));
        assertThrows(IllegalArgumentException.class, () -> material.lookup("49"));
        assertThrows(IllegalArgumentException.class, () -> material.lookup("29"));
        assertThrows(IllegalArgumentException.class, () -> material.lookup(null));
        assertThrows(IllegalArgumentException.class, () -> material.lookup("35.5"));
        assertThrows(IllegalArgumentException.class, () -> material.lookup("2147483648"));
        assertThrows(IllegalArgumentException.class, () -> curves.require("missing"));
    }

    @Test
    void rejectOverlappingMalformedAndNonexistentCurveRows() {
        for (String yaml : java.util.List.of(
                "material:\n  \"0~5\": \"1\"\n  \"5~9\": \"2\"\n",
                "material:\n  \"9~5\": \"1\"\n",
                "material:\n  \"5~\": \"1\"\n  \"10~15\": \"2\"\n",
                "material:\n  \"3~4\": \"1+1:101\"\n",
                "material:\n  \"3~4\": \"1+1:foo\"\n",
                "material:\n  \"3~4\": 2\n",
                "material:\n  bad: \"1\"\n",
                "material:\n  \"2147483648~\": \"1\"\n",
                "material: {}\n",
                "- not-a-mapping\n")) {
            assertThrows(IllegalArgumentException.class, () -> RewardCurves.parse(yaml), yaml);
        }
    }
}
