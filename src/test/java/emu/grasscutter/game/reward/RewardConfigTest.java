package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.reward.ChestRewardConfig.Kind;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Integration tests for the one-file, two-subsystem configuration boundary. */
final class RewardConfigTest {
    @TempDir Path temp;

    private static String bundled() throws Exception {
        try (var in = RewardConfig.class.getResourceAsStream("/rewards.toml")) {
            assertNotNull(in);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Test
    void bundledFileContainsBothSubsystemsAndNativeTransportExceptions() throws Exception {
        var config = RewardConfig.parse(bundled());
        assertNotNull(config.chest());
        assertNotNull(config.transPoint());
        assertEquals("kind=common,area=3:1:104",
                config.chest().resolve(Kind.COMMON, 3, 1, 104).selector());
        assertEquals(1, config.transPoint().resolve(
                3, 6, TransPointRewardConfig.Kind.WAYPOINT).size());
        assertEquals(102, config.transPoint().resolve(
                3, 6, TransPointRewardConfig.Kind.WAYPOINT).get(0).getId());
        assertEquals(10, config.transPoint().resolve(
                3, 6, TransPointRewardConfig.Kind.WAYPOINT).get(0).getCount());
        assertEquals(2, config.transPoint().resolve(
                7, 2, TransPointRewardConfig.Kind.WAYPOINT).size());
    }

    @Test
    void rejectsMissingSectionsUnknownRootsAndBadVersion() throws Exception {
        String text = bundled();
        assertThrows(IllegalArgumentException.class, () -> RewardConfig.parse(
                text.replaceFirst("(?m)^version = 1\\n", "")));
        assertThrows(IllegalArgumentException.class, () -> RewardConfig.parse(
                text.replaceFirst("(?m)^version = 1", "version = 2")));
        assertThrows(IllegalArgumentException.class, () -> RewardConfig.parse(
                text.replaceFirst("(?m)^version = 1", "version = 1\\nunknown = true")));
        assertThrows(IllegalArgumentException.class, () -> RewardConfig.parse(
                text.replaceFirst("(?m)^\\[transPoint\\.\\\"kind=waypoint\\\"\\]",
                        "[transPoint.\\\"kind=unknown\\\"]")));
        assertThrows(IllegalArgumentException.class, () -> RewardConfig.parse(
                text.replaceFirst("(?m)^\\[chest\\.\\\"kind=common\\\"\\]",
                        "[chest.\\\"kind=unknown\\\"]")));
    }

    @Test
    void badEditsInEitherSubsystemCannotReplaceTheActivePair() throws Exception {
        Path file = temp.resolve("rewards.toml");
        RewardConfig.loadOrCreate(file);
        RewardConfig before = RewardConfig.current();

        Files.writeString(file, bundled().replace(
                "[transPoint.\"kind=waypoint\"]",
                "[transPoint.\"kind=not-a-waypoint\"]"));
        assertThrows(IllegalStateException.class, () -> RewardConfig.loadOrCreate(file));
        assertSame(before, RewardConfig.current());

        Files.writeString(file, bundled().replace(
                "[chest.\"kind=common\"]",
                "[chest.\"kind=not-a-chest\"]"));
        assertThrows(IllegalStateException.class, () -> RewardConfig.loadOrCreate(file));
        assertSame(before, RewardConfig.current());
    }
}
