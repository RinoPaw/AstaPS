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
        assertEquals(Map.of(102, 30, 201, 2),
                amounts(cfg.chest().resolve(Kind.COMMON, 3, 1, 104).rewards()));
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
    void refusesToIgnoreLegacyTomlOnFirstCssStart() throws Exception {
        Path old = temp.resolve("rewards.toml");
        Path current = temp.resolve("rewards.css");
        Files.writeString(old, "# manually customized config\n");
        var ex = assertThrows(IllegalStateException.class,
                () -> RewardConfig.loadOrCreate(current));
        assertTrue(ex.getMessage().contains("rewards.toml"));
        assertFalse(Files.exists(current));
        assertEquals("# manually customized config\n", Files.readString(old));
        // Once an explicit CSS is present it is authoritative; the old file is never read.
        Files.writeString(current, bundled());
        RewardConfig.loadOrCreate(current);
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
}
