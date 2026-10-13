package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class NodKraiExploreSpawnHelperChestTagTest {
    private static String tag(int id, String kind) {
        return NodKraiExploreSpawnHelper.dropTagForKind(
                new NodKraiExploreSpawnHelper.ExplorePoint(id, kind, 70211101, 0, null));
    }

    @Test
    void moraChestsUseNativeCurrencyOnlyDropTags() {
        assertEquals("\u6469\u62c9\u77f3\u7bb1\u67ab\u4e39", tag(20179, "chest_mora"));
        assertEquals("\u6469\u62c9\u77f3\u7bb1\u7eb3\u5854", tag(30363, "chest_mora"));
        assertEquals("\u6469\u62c9\u77f3\u7bb1\u987b\u5f25", tag(40096, "chest_mora"));
    }

    @Test
    void ordinarySumeruChestKeepsNativePuzzleTier() {
        assertEquals("\u89e3\u8c1c\u4e2d\u7ea7\u987b\u5f25",
                tag(40000, "chest_exquisite"));
    }
}
