package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.reward.ChestRewardConfig.Kind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChestRewardConfigTest {
    @TempDir Path tempDir;

    private static String template() throws IOException {
        try (var input = ChestRewardConfig.class.getResourceAsStream("/rewards.toml")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<Integer, Integer> contents(ChestRewardConfig.Match match) {
        return match.rewards().stream()
                .collect(Collectors.toMap(ItemParamData::getId, ItemParamData::getCount));
    }

    private static String minimalRules() {
        return """
                [chest."kind=common"]
                adventureExp = 30
                primogems = 2

                [chest."kind=exquisite"]
                adventureExp = 30
                primogems = 5

                [chest."kind=precious"]
                adventureExp = 30
                primogems = 10

                [chest."kind=luxurious"]
                adventureExp = 30
                primogems = 10
                """;
    }

    @Test
    void templateDefinesFourDefaultQualitiesAndCompleteOverrides() throws IOException {
        var config = ChestRewardConfig.parse(template());
        assertEquals(Map.of(102, 30, 201, 2), contents(config.resolve(Kind.COMMON, 3, 10, 1501)));
        assertEquals(Map.of(102, 30, 201, 5), contents(config.resolve(Kind.EXQUISITE, 3, 8, 801)));
        assertEquals(Map.of(102, 30, 201, 10), contents(config.resolve(Kind.PRECIOUS, 6, 300, 0)));
        assertEquals(Map.of(102, 30, 201, 10), contents(config.resolve(Kind.LUXURIOUS, 3, 2, 201)));
        assertEquals("kind=luxurious", config.resolve(Kind.LUXURIOUS, 3, 2, 201).selector());
    }

    @Test
    void historicLowAreasAreLimitedToExplicitlyConfiguredParents() throws IOException {
        var config = ChestRewardConfig.parse(template());
        for (int parent : new int[] {1, 2, 3, 4, 5, 6, 7, 9}) {
            assertEquals(
                    Map.of(102, 30),
                    contents(config.resolve(Kind.COMMON, 3, parent, 0)),
                    "Low ordinary chest parent " + parent);
            assertEquals(
                    Map.of(102, 30, 201, 2),
                    contents(config.resolve(Kind.EXQUISITE, 3, parent, 0)));
            assertEquals(
                    Map.of(102, 30, 201, 5),
                    contents(config.resolve(Kind.PRECIOUS, 3, parent, 0)));
        }
        // Newly added Mondstadt and Liyue regions MUST NOT inherit old-city rates.
        for (int parent : new int[] {10, 19, 40, 41, 47, 77}) {
            assertEquals(
                    Map.of(102, 30, 201, 2),
                    contents(config.resolve(Kind.COMMON, 3, parent, 0)));
        }
        assertEquals(Map.of(102, 30, 201, 2), contents(config.resolve(Kind.COMMON, 6, 300, 0)));
    }

    @Test
    void exactLevelTwoOverridesLevelOneAndKind() throws IOException {
        var config = ChestRewardConfig.parse(template());
        assertEquals("kind=common,area=3:1:104", config.resolve(Kind.COMMON, 3, 1, 104).selector());
        assertEquals(Map.of(102, 30, 201, 2), contents(config.resolve(Kind.COMMON, 3, 1, 104)));
        assertEquals(Map.of(102, 30), contents(config.resolve(Kind.COMMON, 3, 1, 105)));
        assertEquals("kind=precious,area=3:7:703", config.resolve(Kind.PRECIOUS, 3, 7, 703).selector());
        assertEquals(Map.of(102, 30, 201, 10), contents(config.resolve(Kind.PRECIOUS, 3, 7, 703)));
        assertEquals(Map.of(102, 30, 201, 5), contents(config.resolve(Kind.PRECIOUS, 3, 7, 702)));
        assertEquals(Map.of(102, 30, 201, 2), contents(config.resolve(Kind.COMMON, 99, 0, 0)));
    }

    @Test
    void laterMatchingRuleOverridesEarlierValuesRegardlessOfSpecificity() {
        // The second, broader rule must win when it occurs later in the file.
        String text = minimalRules()
                + "\n[chest.\"kind=common,area=3:1:104\"]\nprimogems = 9\n"
                + "\n[chest.\"kind=common,area=3:1\"]\nprimogems = 1\n";
        var config = ChestRewardConfig.parse(text);
        assertEquals(Map.of(102, 30, 201, 1), contents(config.resolve(Kind.COMMON, 3, 1, 104)));
        assertEquals("kind=common,area=3:1",
                config.resolve(Kind.COMMON, 3, 1, 104).selector());
        assertEquals(Map.of(102, 30, 201, 1), contents(config.resolve(Kind.COMMON, 3, 1, 109)));
        assertEquals(Map.of(102, 30, 201, 2), contents(config.resolve(Kind.COMMON, 3, 2, 0)));
    }

    @Test
    void laterSpecificRuleOverridesEarlierGeneralRule() {
        var config = ChestRewardConfig.parse(minimalRules()
                + "\n[chest.\"kind=common,area=3:1\"]\nprimogems = 1\n"
                + "\n[chest.\"kind=common,area=3:1:104\"]\nprimogems = 9\n");
        assertEquals(Map.of(102, 30, 201, 9), contents(config.resolve(Kind.COMMON, 3, 1, 104)));
        assertEquals("kind=common,area=3:1:104",
                config.resolve(Kind.COMMON, 3, 1, 104).selector());
    }

    @Test
    void evenLaterKindDefaultOverridesEarlierAreaRule() {
        String text = """
                [chest."kind=common,area=3:1:104"]
                primogems = 9

                [chest."kind=common,area=3:1"]
                primogems = 1

                [chest."kind=common"]
                primogems = 2
                adventureExp = 30

                [chest."kind=exquisite"]
                primogems = 5

                [chest."kind=precious"]
                primogems = 10

                [chest."kind=luxurious"]
                primogems = 10
                """;
        var config = ChestRewardConfig.parse(text);
        assertEquals(Map.of(102, 30, 201, 2), contents(config.resolve(Kind.COMMON, 3, 1, 104)));
        assertEquals("kind=common", config.resolve(Kind.COMMON, 3, 1, 104).selector());
    }

    @Test
    void laterRulesOnlyOverrideFieldsTheySpecify() {
        var config = ChestRewardConfig.parse(minimalRules()
                + "\n[chest.\"kind=common,area=3:1\"]\nprimogems = 0\n");
        var match = config.resolve(Kind.COMMON, 3, 1, 104);
        assertEquals("kind=common,area=3:1", match.selector());
        assertEquals(Map.of(102, 30), contents(match));

        var another = ChestRewardConfig.parse(minimalRules()
                + "\n[chest.\"kind=common,area=3:1\"]\nadventureExp = 11\n");
        assertEquals(Map.of(102, 11, 201, 2),
                contents(another.resolve(Kind.COMMON, 3, 1, 101)));
    }

    @Test
    void itemOverridesCanReplaceOrRemoveEarlierItems() {
        String text = minimalRules().replace(
                "[chest.\"kind=common\"]\n",
                "[chest.\"kind=common\"]\n"
                        + "items = [{ id = 104001, count = 2 }, { id = 104002, count = 1 }]\n")
                + "\n[chest.\"kind=common,area=3:1\"]\n"
                + "items = [{ id = 104001, count = 7 }, { id = 104002, count = 0 }]\n";
        var config = ChestRewardConfig.parse(text);
        assertEquals(Map.of(102, 30, 201, 2, 104001, 7),
                contents(config.resolve(Kind.COMMON, 3, 1, 0)));
        assertEquals(Map.of(102, 30, 201, 2, 104001, 2, 104002, 1),
                contents(config.resolve(Kind.COMMON, 3, 2, 0)));
    }

    @Test
    void missingChildBoundaryOnlyMatchesParent() {
        var config = ChestRewardConfig.parse(minimalRules()
                + "\n[chest.\"kind=common,area=3:1:104\"]\nprimogems = 9\n");
        assertEquals(Map.of(102, 30, 201, 2), contents(config.resolve(Kind.COMMON, 3, 1, 0)));
        assertEquals(Map.of(201, 9), contents(config.resolve(Kind.COMMON, 3, 1, 104)));
        assertThrows(IllegalArgumentException.class, () -> config.resolve(Kind.COMMON, -1, 1, 1));
        assertThrows(NullPointerException.class, () -> config.resolve(null, 3, 1, 1));
    }

    @Test
    void missingDefaultOrInvalidSelectorIsRejected() {
        String rules = minimalRules();
        assertThrows(IllegalArgumentException.class,
                () -> ChestRewardConfig.parse(rules.replace("kind=precious", "kind=remarkable")));
        for (String selector : List.of(
                "kind=remarkable",
                "city=1,kind=common",
                "kind=common,city=1",
                "area=3:1,kind=common",
                "kind=common,area=3:1:*",
                "kind=common,area=3:0",
                "kind=common,area=03:1",
                "kind=common,area=3:1:0",
                "kind=common,area=2147483648:1",
                "kind=common,area=3:2147483648",
                "kind=common,area=3:1:2147483648",
                "kind=common,area=3:1,area=3:2",
                "kind=common,area=3:1,source=quest")) {
            String text = rules + "\n[chest.\"" + selector + "\"]\nprimogems = 0\n";
            assertThrows(IllegalArgumentException.class, () -> ChestRewardConfig.parse(text), selector);
        }
    }

    @Test
    void unknownSectionsAndTomlSyntaxErrorsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ChestRewardConfig.parse(minimalRules() + "\n[transPoint]\nprimogems = 4\n"));
        assertThrows(IllegalArgumentException.class,
                () -> ChestRewardConfig.parse(minimalRules() + "\nversion = 1\n"));
        assertThrows(IllegalArgumentException.class,
                () -> ChestRewardConfig.parse(minimalRules() + "\n[chest.\"kind=common\"]\nprimogems = 1\n"));
        assertThrows(IllegalArgumentException.class, () -> ChestRewardConfig.parse(""));
        assertThrows(IllegalArgumentException.class,
                () -> ChestRewardConfig.parse(minimalRules().replace("primogems = 2", "primogems = ")));
    }

    @Test
    void rewardAmountsAreStronglyTypedAndLimitedToDirectRewards() {
        String rules = minimalRules();
        for (String invalid : List.of(
                "adventureExp = -1", "adventureExp = 1.5", "adventureExp = \"3\"",
                "primogems = -1", "primogems = 2147483648", "primogems = true",
                "source = \"native\"", "dropId = 51010000",
                "drop_tag = \"战斗低级蒙德\"", "kind = \"common\"",
                "area = \"3:1\"", "items = \"not an array\"")) {
            String text = rules.replace(
                    "[chest.\"kind=common\"]\n",
                    "[chest.\"kind=common\"]\n" + invalid + "\n");
            assertThrows(IllegalArgumentException.class, () -> ChestRewardConfig.parse(text), invalid);
        }
    }

    @Test
    void itemsMustHavePositiveUniqueIdsAndCounts() {
        String rules = minimalRules();
        String header = "[chest.\"kind=common\"]\n";
        String valid = rules.replace(header,
                header + "items = [{ id = 104001, count = 2 }, { id = 104002, count = 1 }]\n");
        assertEquals(Map.of(102, 30, 201, 2, 104001, 2, 104002, 1),
                contents(ChestRewardConfig.parse(valid).resolve(Kind.COMMON, 0, 0, 0)));
        for (String line : List.of(
                "items = [{ id = 0, count = 1 }]",
                "items = [{ id = 104001, count = -1 }]",
                "items = [{ id = 104001, count = 1, chance = 0.1 }]",
                "items = [{ id = 104001 }]",
                "items = [104001]",
                "items = [{ id = 104001, count = 1 }, { id = 104001, count = 2 }]",
                "items = [{ id = 201, count = 3 }]")) {
            String text = rules.replace(header, header + line + "\n");
            assertThrows(IllegalArgumentException.class, () -> ChestRewardConfig.parse(text), line);
        }
    }

    @Test
    void createsRootFileOnlyOnceAndPreservesOperatorEditsAndActiveSnapshot() throws IOException {
        Path path = tempDir.resolve("rewards.toml");
        assertFalse(Files.exists(path));
        ChestRewardConfig.loadOrCreate(path);
        assertEquals(template(), Files.readString(path));
        assertEquals(Map.of(102, 30, 201, 2),
                contents(ChestRewardConfig.current().resolve(Kind.COMMON, 3, 10, 0)));

        String customized = minimalRules().replace("primogems = 2", "primogems = 7");
        Files.writeString(path, customized);
        ChestRewardConfig.loadOrCreate(path);
        assertEquals(customized, Files.readString(path));
        var previous = ChestRewardConfig.current();
        assertEquals(Map.of(102, 30, 201, 7),
                contents(previous.resolve(Kind.COMMON, 3, 1, 0)));

        Files.writeString(path, "invalid = true\n");
        assertThrows(IllegalStateException.class, () -> ChestRewardConfig.loadOrCreate(path));
        assertEquals(previous, ChestRewardConfig.current());
        assertEquals("invalid = true\n", Files.readString(path));
    }
}
