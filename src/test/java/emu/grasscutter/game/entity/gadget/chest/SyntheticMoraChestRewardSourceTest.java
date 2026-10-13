package emu.grasscutter.game.entity.gadget.chest;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class SyntheticMoraChestRewardSourceTest {
    @Test
    void onlySpecificSyntheticMoraTagsMatchTheirRegion() {
        assertTrue(SyntheticMoraChestRewardSource.applies(910740001, "摩拉石箱须弥"));
        assertTrue(SyntheticMoraChestRewardSource.applies(910720001, "摩拉石箱枫丹"));
        assertTrue(SyntheticMoraChestRewardSource.applies(910730001, "摩拉石箱纳塔"));
        assertFalse(SyntheticMoraChestRewardSource.applies(910730001, "摩拉石箱须弥"));
        assertFalse(SyntheticMoraChestRewardSource.applies(910700267, "摩拉石箱挪德卡莱"));
        assertFalse(SyntheticMoraChestRewardSource.applies(910710141, "摩拉石箱至冬"));
        assertFalse(SyntheticMoraChestRewardSource.applies(910810141, "摩拉石箱至冬"));
        assertFalse(SyntheticMoraChestRewardSource.applies(133302298, "摩拉石箱枫丹"));
        assertFalse(SyntheticMoraChestRewardSource.applies(910720001, "解谜中级枫丹"));
        assertFalse(SyntheticMoraChestRewardSource.applies(910720001, null));
    }
}
