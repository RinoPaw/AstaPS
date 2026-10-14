package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.reward.ChestRewardConfig.Kind;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RewardConfigTest {
    @TempDir Path temp;

    private static String bundled() throws Exception {
        try (var in = RewardConfig.class.getResourceAsStream("/rewards.css")) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<Integer,Integer> amounts(java.util.List<ItemParamData> values) {
        return values.stream().collect(Collectors.toMap(ItemParamData::getId, ItemParamData::getCount));
    }

    @Test
    void bundledPolicyParsesBothKindsOfReward() throws Exception {
        var cfg = RewardConfig.parse(bundled());
        var chest = amounts(cfg.chest().resolve(Kind.COMMON, 3, 1, 104).rewards());
        assertEquals(30, chest.get(102));
        assertEquals(2, chest.get(201));
        assertFalse(chest.containsKey(202));
        assertFalse(chest.containsKey(104011));
        assertEquals(Map.of(102, 10),
                amounts(cfg.transPoint().resolve(3, 6, TransPointRewardConfig.Kind.WAYPOINT)));
        assertEquals(Map.of(201, 5, 101692, 20),
                amounts(cfg.transPoint().resolve(7, 9, TransPointRewardConfig.Kind.DOMAIN)));
    }

    @Test
    void preservesExistingOperatorCssWithoutSilentResetAndPublishesAtomically() throws Exception {
        Path file = temp.resolve("rewards.css");
        RewardConfig.loadOrCreate(file);
        assertEquals(bundled(), Files.readString(file));
        var before = RewardConfig.current();
        String modified = bundled().replace(
                "chest[kind=\"common\"] {\n    adventureExp: 30;\n    primogems: 2;",
                "chest[kind=\"common\"] {\n    adventureExp: 30;\n    primogems: 7;");
        assertNotEquals(bundled(), modified);
        Files.writeString(file, modified);
        RewardConfig.loadOrCreate(file);
        assertEquals(modified, Files.readString(file));
        assertEquals(7, RewardConfig.current().chest()
                .resolve(Kind.COMMON, 3, 10, 1).rewards().stream()
                .filter(v -> v.getId() == 201).findFirst().orElseThrow().getCount());
        var valid = RewardConfig.current();
        Files.writeString(file, "chest[kind=\"common\"] { primogems: 2; }");
        assertThrows(IllegalStateException.class, () -> RewardConfig.loadOrCreate(file));
        assertSame(valid, RewardConfig.current());
        assertNotSame(before, valid);
    }

    @Test
    void missingCssGeneratesDefaultsEvenIfOldTomlExists() throws Exception {
        Path old = temp.resolve("rewards.toml");
        Path current = temp.resolve("rewards.css");
        Files.writeString(old, "# legacy file\n");
        RewardConfig.loadOrCreate(current);
        assertEquals(bundled(), Files.readString(current));
        assertEquals("# legacy file\n", Files.readString(old));
        assertNotNull(RewardConfig.current().transPoint());
    }

    @Test
    void unknownAtRulesAndSyntaxErrorsCannotStartPartially() throws Exception {
        String css = bundled();
        assertThrows(IllegalArgumentException.class,
                () -> RewardConfig.parse(css + "\n@supports (display: grid) {chest { primogems: 3; }}"));
        assertThrows(IllegalArgumentException.class,
                () -> RewardConfig.parse(css + "\nother { primogems: 2; }"));
        assertThrows(IllegalArgumentException.class,
                () -> RewardConfig.parse(""));
        assertThrows(IllegalArgumentException.class,
                () -> RewardConfig.parse("chest[kind=\"common\"] { primogems: 2; "));
    }
    @Test
    void oldOperatorCssInheritsDefaultsInMemoryWithoutWritingFiles() throws Exception {
        String source = bundled();
        int chestStart = source.indexOf("/* Chest: four standard exploration qualities */");
        assertTrue(chestStart > 0);
        String legacy = source.substring(0, chestStart)
                + source.substring(chestStart).replaceAll(
                        "(?m)^    (?:mora|sigils|item-[0-9]+): [0-9]+;\r?\n", "");
        legacy += """

                /* User-customized higher-specificity regional override */
                chest[kind="common"][scene="3"][area1="1"] { primogems: 0; item-104001: 0; }
                """;
        // Include a user edit to a base declaration. No migration may overwrite it.
        legacy = legacy.replace("chest[kind=\"common\"] {\n    adventureExp: 30;\n    primogems: 2;",
                "chest[kind=\"common\"] {\n    adventureExp: 30;\n    primogems: 7;");
        Path file = temp.resolve("rewards.css");
        Files.writeString(file, legacy);
        RewardConfig.loadOrCreate(file);

        assertEquals(legacy, Files.readString(file));
        assertEquals(7, RewardConfig.current().chest().resolve(Kind.COMMON, 3, 10, 0)
                .amounts().get(201));
        assertEquals(0, RewardConfig.current().chest().resolve(Kind.COMMON, 3, 1, 0)
                .amounts().get(201));
        assertEquals(0, RewardConfig.current().chest().resolve(Kind.COMMON, 3, 1, 0)
                .amounts().get(104001));
        assertFalse(RewardConfig.current().chest().resolve(Kind.COMMON, 3, 1, 0)
                .amounts().containsKey(202));
        assertFalse(RewardConfig.current().chest().resolve(Kind.COMMON, 3, 1, 0)
                .amounts().containsKey(-1));
        assertEquals(20, RewardConfig.current().transPoint()
                .resolve(7, 9, TransPointRewardConfig.Kind.DOMAIN).stream()
                .filter(item -> item.getId() == 101692).findFirst().orElseThrow().getCount());
        assertFalse(RewardConfig.current().chest().resolve(Kind.LUXURIOUS, 3, 1, 0)
                .amounts().containsKey(202));

        // Reloading uses the same defaults but creates no additional files.
        RewardConfig.loadOrCreate(file);
        assertEquals(legacy, Files.readString(file));
        try (var files = Files.list(temp)) {
            assertEquals(java.util.List.of("rewards.css"),
                    files.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void oldOperatorCssInheritsMissingNativePoolRulesOnlyInMemory() throws Exception {
        String all = bundled();
        int start = all.indexOf("/* Only for ordinary chests WITHOUT");
        int end = all.indexOf("/* Historical low-Primogem areas only");
        assertTrue(start > 0 && end > start);
        String old = all.substring(0, start) + all.substring(end);
        // The operator disables fallback Mora; inherited CSS must not overwrite zero.
        old += "\nchest[kind=\"common\"][pool=\"missing\"] { mora: 0; }\n";

        Path file = temp.resolve("rewards.css");
        Files.writeString(file, old);
        RewardConfig.loadOrCreate(file);

        assertEquals(old, Files.readString(file));
        var chest = RewardConfig.current().chest();
        assertFalse(chest.resolve(Kind.COMMON, 0, 0, 0).amounts().containsKey(202));
        var missing = chest.resolve(Kind.COMMON, 0, 0, 0, true).amounts();
        assertEquals(0, missing.get(202));
        assertEquals(3, missing.get(-1));
        assertEquals(2, missing.get(104011));
        assertEquals(30, missing.get(102));
        assertEquals(2500, chest.resolve(Kind.LUXURIOUS, 0, 0, 0, true)
                .amounts().get(202));
    }

    @Test
    void malformedCssIsNotAutoFixedAndActiveSnapshotIsKept() throws Exception {
        Path file = temp.resolve("rewards.css");
        RewardConfig.loadOrCreate(file);
        var previous = RewardConfig.current();
        String invalid = bundled() + "\nchest[kind=\"common\"] { primogems: -2; }\n";
        Files.writeString(file, invalid);
        assertThrows(IllegalStateException.class, () -> RewardConfig.loadOrCreate(file));
        assertSame(previous, RewardConfig.current());
        assertEquals(invalid, Files.readString(file));
        try (var files = Files.list(temp)) {
            assertEquals(0, files.filter(p -> p.getFileName().toString()
                    .contains(".backup-")).count());
        }
    }

    @Test
    void validUpdatedCssRemainsByteForByteUnmodified() throws Exception {
        Path file = temp.resolve("rewards.css");
        String updated = "/* my CSS */\n" + bundled();
        Files.writeString(file, updated);
        RewardConfig.loadOrCreate(file);
        assertEquals(updated, Files.readString(file));
        try (var files = Files.list(temp)) {
            assertEquals(0, files.filter(p -> p.getFileName().toString()
                    .contains(".backup-")).count());
        }
    }

}
