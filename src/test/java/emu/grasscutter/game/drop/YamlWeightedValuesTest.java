package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class YamlWeightedValuesTest {
    @Test
    void compactAndExplicitChoicesReturnIdenticalStrings() {
        var compact = YamlWeightedValues.compile(
                List.of(Map.of("A", 3), Map.of("B", 1)),
                value -> (String) value, "compact");
        var explicit = YamlWeightedValues.compile(
                List.of(Map.of("value", "A", "weight", 3),
                        Map.of("value", "B", "weight", 1)),
                value -> (String) value, "explicit");
        for (int seed = 0; seed < 200; seed++) {
            assertEquals(compact.sample(new Random(seed)), explicit.sample(new Random(seed)));
        }
    }

    @Test
    void choicesDoNotAssumeItemsOrStringValues() {
        var structured = Map.of("rarity", 4, "slot", List.of("plume", "goblet"));
        var candidate = YamlWeightedValues.compile(
                List.of(Map.of("value", structured, "weight", 5)),
                value -> (Map<?, ?>) value, "structured");
        assertEquals(structured, candidate.sample(new Random(71)));

        var numbers = YamlWeightedValues.compile(
                List.of(Map.of(42, 1)), value -> (Integer) value, "integers");
        assertEquals(42, numbers.sample(new Random(71)));

        var lists = YamlWeightedValues.compile(
                List.of(Map.of("value", List.of("a", "b"), "weight", 1)),
                value -> (List<?>) value, "lists");
        assertEquals(List.of("a", "b"), lists.sample(new Random(71)));
    }

    @Test
    void incompleteWeightsDoNotBecomeUnweightedSelections() {
        assertThrows(IllegalArgumentException.class, () -> YamlWeightedValues.compile(
                List.of(Map.of("value", "A", "weight", 1), Map.of("value", "B")),
                value -> value, "invalid"));
        assertThrows(IllegalArgumentException.class, () -> YamlWeightedValues.compile(
                List.of(Map.of("value", "A", "weight", -1)),
                value -> value, "negative"));
    }
}
