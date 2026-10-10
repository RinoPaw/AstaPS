package emu.grasscutter.game.dungeons;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.game.dungeons.enums.DungeonSubType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Coverage for three Fontaine weapon families missing their native 7.1 drop roots.
 * Item IDs and guaranteed values are validated against pinned resource previews.
 * Material count distributions remain compatibility estimates, not native rates.
 */
final class FontaineWeaponProxyCoverageTest {
    @Test
    void allTwelveFontaineWeaponDungeonsHaveSourceMatchedProxyRewards() throws Exception {
        var drops = new Gson().fromJson(
                Files.readString(Path.of("data", "DungeonDrop.json")), DungeonDrop[].class);
        Map<Integer, DungeonDrop> byId = Arrays.stream(drops)
                .collect(Collectors.toMap(DungeonDrop::getDungeonId, Function.identity()));
        int[] firstDungeon = {4470, 4500, 4504};
        int[] firstMaterial = {114049, 114053, 114057};
        int[] mora = {1125, 1550, 1850, 2200};
        int[] friendship = {10, 15, 15, 20};

        for (int family = 0; family < 3; family++) {
            for (int tier = 0; tier < 4; tier++) {
                int id = firstDungeon[family] + tier;
                int materialStart = firstMaterial[family];
                var row = byId.get(id);
                assertNotNull(row, "Missing Fontaine weapon proxy " + id);
                DomainDropSafety.validatePool(
                        id, row.getDrops(), DungeonSubType.DUNGEON_SUB_WEAPON);
                var terminalIds = row.getDrops().stream()
                        .flatMap(entry -> entry.getItems().stream())
                        .collect(Collectors.toSet());
                var expected = new java.util.HashSet<>(Set.of(102, 202, 105));
                for (int rank = 0; rank <= tier; rank++) {
                    expected.add(materialStart + rank);
                }
                assertEquals(expected, terminalIds, "Wrong family or rarity for " + id);

                var guaranteed = row.getDrops().stream()
                        .collect(Collectors.toMap(entry -> entry.getItems().getFirst(), Function.identity()));
                assertEquals(100, guaranteed.get(102).getCounts().getFirst());
                assertEquals(mora[tier], guaranteed.get(202).getCounts().getFirst());
                assertEquals(friendship[tier], guaranteed.get(105).getCounts().getFirst());
            }
        }
    }
}
