package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

final class NodKraiExploreSpawnHelperMoraTagTest {
    private static String tag(int id, String kind) {
        return NodKraiExploreSpawnHelper.dropTagForKind(
                new NodKraiExploreSpawnHelper.ExplorePoint(id, kind, 70211101, 0, null));
    }

    @Test
    void regionMoraMarkersUseExistingMoraOnlyPools() {
        assertEquals("摩拉石箱须弥", tag(40001, "chest_mora"));
        assertEquals("摩拉石箱枫丹", tag(20001, "chest_mora"));
        assertEquals("摩拉石箱纳塔", tag(30001, "chest_mora"));
    }

    @Test
    void noInventedMoraPoolForNodKrai() {
        assertEquals("解谜低级挪德卡莱", tag(267, "chest_mora"));
    }

    @Test
    void ordinaryChestClassificationIsUnchanged() {
        assertEquals("解谜中级须弥", tag(40001, "chest_exquisite"));
        assertEquals("解谜超级至冬", tag(10141, "chest_remarkable"));
    }
}
