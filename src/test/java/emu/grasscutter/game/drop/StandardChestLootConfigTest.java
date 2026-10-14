package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.excels.DropTableData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StandardChestLootConfigTest {
    private static final String COMMON_TAG = "战斗低级蒙德";
    private static final String EXQUISITE_TAG = "解谜中级璃月";

    @TempDir Path directory;

    private static StandardChestLootConfig.Document withCommon(
            List<StandardChestLootConfig.Source> sources,
            List<StandardChestLootConfig.Table> tables) {
        var kinds = new LinkedHashMap<String, StandardChestLootConfig.Quality>();
        for (String kind : List.of("common", "exquisite", "precious", "luxurious")) {
            kinds.put(kind, new StandardChestLootConfig.Quality(List.of(), List.of()));
        }
        kinds.put("common", new StandardChestLootConfig.Quality(sources, tables));
        return new StandardChestLootConfig.Document(kinds);
    }

    @Test
    void configuredWeightedAndIndependentRollsUseTheirOwnNumbers() {
        var cfg = new StandardChestLootConfig(withCommon(
                List.of(new StandardChestLootConfig.Source(COMMON_TAG, 1, 51010000)),
                List.of(
                        new StandardChestLootConfig.Table(51010000, 0, true,
                                List.of(new StandardChestLootConfig.Drop(51010001, "2", 7))),
                        new StandardChestLootConfig.Table(51010001, 1, false, List.of(
                                new StandardChestLootConfig.Drop(202, "3;3", 10000),
                                new StandardChestLootConfig.Drop(104001, "1", 10000))))));
        var payout = cfg.roll(COMMON_TAG, 1, new Random(11));
        assertNotNull(payout);
        assertEquals(Map.of(202, 6, 104001, 2), payout.amounts());
        assertTrue(payout.fallToGround());
        assertNull(cfg.roll(EXQUISITE_TAG, 1, new Random(11)));
    }

    @Test
    void sourcesChooseHighestEligibleLevelWithoutBorrowingOtherKinds() {
        var cfg = new StandardChestLootConfig(withCommon(
                List.of(new StandardChestLootConfig.Source(COMMON_TAG, 1, 10),
                        new StandardChestLootConfig.Source(COMMON_TAG, 21, 20)),
                List.of(new StandardChestLootConfig.Table(10, 1, false,
                                List.of(new StandardChestLootConfig.Drop(202, "50", 10000))),
                        new StandardChestLootConfig.Table(20, 1, false,
                                List.of(new StandardChestLootConfig.Drop(202, "100", 10000))))));
        assertEquals(50, cfg.roll(COMMON_TAG, 5, new Random(0)).amounts().get(202));
        assertEquals(100, cfg.roll(COMMON_TAG, 21, new Random(0)).amounts().get(202));
        assertNull(cfg.roll(COMMON_TAG, 0, new Random(0)));
        assertNull(cfg.roll(EXQUISITE_TAG, 30, new Random(0)));
    }

    @Test
    void generatesOnceAndPreservesEditedProbabilities() throws Exception {
        Path file = directory.resolve("chest-loot.json");
        var first = StandardChestLootConfig.loadOrCreate(file, Map.of(), Map.of());
        assertNull(first.roll(COMMON_TAG, 10, new Random(0)));
        String generated = Files.readString(file, StandardCharsets.UTF_8);
        for (String kind : List.of("common", "exquisite", "precious", "luxurious")) {
            assertTrue(generated.contains("\"" + kind + "\""));
        }

        var custom = withCommon(
                List.of(new StandardChestLootConfig.Source(COMMON_TAG, 1, 123)),
                List.of(new StandardChestLootConfig.Table(123, 1, false,
                        List.of(new StandardChestLootConfig.Drop(202, "777", 10000)))));
        Files.writeString(file, new Gson().toJson(custom), StandardCharsets.UTF_8);
        var edited = Files.readString(file, StandardCharsets.UTF_8);
        var reloaded = StandardChestLootConfig.loadOrCreate(file, Map.of(), Map.of());
        assertEquals(Map.of(202, 777), reloaded.roll(COMMON_TAG, 1, new Random(4)).amounts());
        assertEquals(edited, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void exportsOnlyOrdinaryTagsAndReachableSubtables() {
        var gson = new Gson();
        BaseDropData chest = gson.fromJson(
                "{\"index\":\"战斗低级蒙德\",\"minLevel\":1,\"dropId\":51010000}",
                BaseDropData.class);
        BaseDropData boss = gson.fromJson(
                "{\"index\":\"Boss\",\"minLevel\":1,\"dropId\":9999}",
                BaseDropData.class);
        DropTableData root = gson.fromJson(
                "{\"id\":51010000,\"randomType\":0,\"fallToGround\":true,"
                        + "\"dropVec\":[{\"itemId\":51010001,\"countRange\":\"1\",\"weight\":100}]}",
                DropTableData.class);
        DropTableData sub = gson.fromJson(
                "{\"id\":51010001,\"randomType\":1,\"dropVec\":["
                        + "{\"itemId\":202,\"countRange\":\"200;300\",\"weight\":10000}]}",
                DropTableData.class);
        var exported = StandardChestLootConfig.export(
                Map.of(COMMON_TAG, List.of(chest), "Boss", List.of(boss)),
                Map.of(51010000, root, 51010001, sub));
        assertEquals(1, exported.chests().get("common").sources().size());
        assertEquals(2, exported.chests().get("common").tables().size());
        assertTrue(exported.chests().get("luxurious").sources().isEmpty());
        int mora = new StandardChestLootConfig(exported)
                .roll(COMMON_TAG, 1, new Random(5)).amounts().get(202);
        assertTrue(mora >= 200 && mora <= 300);
    }

    @Test
    void rejectsBadCountsWeightsDuplicatesAndWrongGrade() {
        assertThrows(IllegalArgumentException.class,
                () -> new StandardChestLootConfig(withCommon(
                        List.of(new StandardChestLootConfig.Source(EXQUISITE_TAG, 1, 10)),
                        List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> new StandardChestLootConfig(withCommon(
                        List.of(new StandardChestLootConfig.Source(COMMON_TAG, 1, 10),
                                new StandardChestLootConfig.Source(COMMON_TAG, 1, 10)),
                        List.of())));
        for (String invalid : List.of("3;2", "-1", "2147483648", "abc")) {
            assertThrows(IllegalArgumentException.class,
                    () -> new StandardChestLootConfig(withCommon(List.of(),
                            List.of(new StandardChestLootConfig.Table(10, 1, false,
                                    List.of(new StandardChestLootConfig.Drop(202, invalid, 10000))))),
                    invalid);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new StandardChestLootConfig(withCommon(List.of(),
                        List.of(new StandardChestLootConfig.Table(10, 1, false,
                                List.of(new StandardChestLootConfig.Drop(202, "1", 10001))))));
    }

    @Test
    void cyclicTablesDoNotHangOrPayPartialLoot() {
        var cfg = new StandardChestLootConfig(withCommon(
                List.of(new StandardChestLootConfig.Source(COMMON_TAG, 1, 10)),
                List.of(new StandardChestLootConfig.Table(10, 1, false,
                        List.of(new StandardChestLootConfig.Drop(10, "1", 10000))))));
        assertThrows(IllegalArgumentException.class,
                () -> cfg.roll(COMMON_TAG, 1, new Random(1)));
    }
}
