package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RewardSelectorConditionsTest {
    private static boolean matches(String text, Map<String, String> attributes) {
        var translation = RewardSelectorConditions.rewrite("chest" + text + " { }");
        assertEquals(1, translation.conditions().size());
        return translation.conditions().values().iterator().next().matches(attributes);
    }

    @Test
    void missingIsDistinctFromPresentAndUnequal() {
        assertTrue(matches("[!grade]", Map.of()));
        assertFalse(matches("[!grade]", Map.of("grade", "")));
        assertFalse(matches("[grade != \"common\"]", Map.of()));
        assertFalse(matches("[grade != \"common\"]", Map.of("grade", "common")));
        assertTrue(matches("[grade != \"common\"]", Map.of("grade", "luxurious")));
    }

    @Test
    void integerAndDecimalComparisonsIncludeRequestedBoundaries() {
        assertTrue(matches("[90 <= level <= 100]", Map.of("level", "90")));
        assertTrue(matches("[90 <= level <= 100]", Map.of("level", "100")));
        assertFalse(matches("[90 <= level <= 100]", Map.of("level", "101")));
        assertFalse(matches("[90 < level < 100]", Map.of("level", "90")));
        assertTrue(matches("[level >= 90]", Map.of("level", "90")));
        assertFalse(matches("[level >= 90]", Map.of("level", "unknown")));
        assertTrue(matches("[value = 1.50]", Map.of("value", "1.5")));
        assertFalse(matches("[value != 1.50]", Map.of("value", "invalid")));
    }

    @Test
    void setMembershipAcceptsValuesOfOneType() {
        assertTrue(matches("[city in (1, 2, 3)]", Map.of("city", "2")));
        assertFalse(matches("[city in (1, 2, 3)]", Map.of("city", "4")));
        assertTrue(matches("[grade in ('common', 'exquisite')]", Map.of("grade", "common")));
        assertFalse(matches("[grade in ('common', 'exquisite')]", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> RewardSelectorConditions.rewrite("chest[city in (1, \"2\")] {}"));
    }

    @Test
    void onlyExtendedPredicatesAreRewritten() {
        String source = """
                /* [level >= 0] */
                chest[grade="common"][level >= 90] {
                    --comment: "[level >= 0]";
                }
                """;
        var translation = RewardSelectorConditions.rewrite(source);
        assertEquals(1, translation.conditions().size());
        assertTrue(translation.css().contains("[grade=\"common\"]"));
        assertTrue(translation.css().contains("\"[level >= 0]\""));
        assertTrue(translation.css().contains("[data-astaps-condition-0]"));
        assertThrows(IllegalArgumentException.class, () ->
                RewardSelectorConditions.rewrite("chest[data-astaps-condition-0] {}"));
    }

    @Test
    void reverseRangesAndIncompatibleTypesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RewardSelectorConditions.rewrite("chest[100 <= level <= 90] {}"));
        assertThrows(IllegalArgumentException.class,
                () -> RewardSelectorConditions.rewrite("chest[level >= \"high\"] {}"));
    }
}
