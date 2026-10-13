package emu.grasscutter.game.dungeons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import emu.grasscutter.game.dungeons.enums.DungeonSubType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Seven 7.1 relic domain proxies reconstructed from the original source DropTable graph.
 *
 * <p>Source: RinoPaw/Genshin-Reverse,
 * versions/7.1.0-global/analyses/progression/economy/7.1-domain-drop-links.json.
 * A valid native root takes precedence over these compatibility pools.
 * This test does not assert that source-missing domains have official rates.
 */
final class NativeReliquaryProxyDataTest {
    @Test
    void sevenNativeBackedPoolsHaveValidArtifactFamiliesAndRolls() throws IOException {
        var text = Files.readString(Path.of("data", "DungeonDrop.json"));
        DungeonDrop[] all = new Gson().fromJson(text, DungeonDrop[].class);
        var byId = new HashMap<Integer, DungeonDrop>();
        for (DungeonDrop d : all) {
            assertTrue(byId.putIfAbsent(d.getDungeonId(), d) == null,
                    "duplicate dungeon " + d.getDungeonId());
        }

        Map<Integer, Set<Integer>> expectedFamilies = Map.of(
                5000, Set.of(513, 601, 602, 603),
                5001, Set.of(513, 602, 603),
                5002, Set.of(513, 602, 603),
                5008, Set.of(543, 632, 633),
                5050, Set.of(204, 214, 543, 544, 583, 584),
                5100, Set.of(523, 612, 613),
                5101, Set.of(523, 612, 613));

        for (var pair : expectedFamilies.entrySet()) {
            int id = pair.getKey();
            var data = byId.get(id);
            assertNotNull(data, "no proxy for dungeon " + id);
            var pool = data.getDrops();
            DomainDropSafety.validatePool(id, pool, DungeonSubType.DUNGEON_SUB_RELIQUARY);

            var artifactIds = new HashSet<Integer>();
            for (var group : pool) {
                for (int itemId : group.getItems()) {
                    assertFalse(itemId >= 114000 && itemId < 115000,
                            "weapon ascension item in reliquary pool " + id);
                    if (itemId > 20000 && itemId < 100000) {
                        artifactIds.add(itemId);
                    }
                }
            }
            assertFalse(artifactIds.isEmpty(), "no artifacts in dungeon " + id);
            var families = new HashSet<Integer>();
            for (int itemId : artifactIds) {
                families.add(itemId / 100);
            }
            assertEquals(pair.getValue(), families, "native reward families of " + id);

            for (int i = 0; i < 25; i++) {
                var rewards = DomainDropRoller.roll(
                        id, DungeonSubType.DUNGEON_SUB_RELIQUARY, pool, 1, false);
                assertFalse(rewards.isEmpty(), "empty payable reward " + id);
                assertTrue(rewards.stream().allMatch(item -> item.getCount() > 0));
                assertTrue(rewards.stream().allMatch(item ->
                        item.getId() == 102 || item.getId() == 202
                                || item.getId() == 105 || artifactIds.contains(item.getId())),
                        "unexpected rolled item in dungeon " + id);
            }
        }
    }
}
