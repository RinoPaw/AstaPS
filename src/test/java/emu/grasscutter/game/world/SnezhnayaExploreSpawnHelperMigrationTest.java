package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SnezhnayaExploreSpawnHelperMigrationTest {
    @Test
    void migratesOnlyKnownSnezhnayaChestsAndOnlyOnce() {
        var source = JsonParser.parseString(
                "{\"10141\":1,\"10142\":1,\"10001\":1,\"20100\":1,\"invalid\":1}"
        ).getAsJsonObject();
        Set<Integer> claimed = new HashSet<>(Set.of(10142));
        Set<Integer> knownChests = Set.of(10141, 10142);

        assertEquals(
                1, SnezhnayaExploreSpawnHelper.importLegacyClaims(source, claimed, knownChests));
        assertEquals(Set.of(10141, 10142), claimed);
        assertEquals(
                0, SnezhnayaExploreSpawnHelper.importLegacyClaims(source, claimed, knownChests));
    }

    @Test
    void guardTimesUseLatestTimestampAndIgnoreNonChestIds() {
        var source = JsonParser.parseString(
                "{\"10141\":100,\"10142\":150,\"10001\":1000,\"20100\":2000,\"invalid\":7}"
        ).getAsJsonObject();
        Map<Integer, Long> timestamps = new HashMap<>(Map.of(10142, 200L));

        assertEquals(
                1, SnezhnayaExploreSpawnHelper.importLegacyGuardTimes(
                        source, timestamps, Set.of(10141, 10142)));
        assertEquals(Map.of(10141, 100L, 10142, 200L), timestamps);
        assertEquals(
                0, SnezhnayaExploreSpawnHelper.importLegacyGuardTimes(
                        source, timestamps, Set.of(10141, 10142)));
    }
}
