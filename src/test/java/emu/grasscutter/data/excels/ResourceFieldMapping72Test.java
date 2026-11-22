package emu.grasscutter.data.excels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.data.excels.avatar.AvatarReplaceCostumeData;
import emu.grasscutter.utils.JsonUtils;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class ResourceFieldMapping72Test {
    @Test
    public void shippedCostumeIdBinds() {
        var row = JsonUtils.decode(
                "{\"avatarId\":10000003,\"replace_costume_id\":200302}",
                AvatarReplaceCostumeData.class);
        assertEquals(200302, row.getCostumeId());
        assertEquals(200302, row.getId());
    }

    @Test
    public void existingObfuscatedAliasesStillBind() {
        assertEquals(300001, JsonUtils.decode(
                "{\"avatarId\":1,\"MGLCOPOIJIC\":300001}",
                AvatarReplaceCostumeData.class).getCostumeId());
        assertEquals(300002, JsonUtils.decode(
                "{\"avatarId\":1,\"BDBMOBGKIAP\":300002}",
                AvatarReplaceCostumeData.class).getCostumeId());
    }

    @Test
    public void shippedBargainKeysAndExistingKeysBind() {
        var row = JsonUtils.decode(
                """
                {"id":1,"quest_id":1013,"item_id":100496,
                "expected_value":[15000,20000],"random_mood":[85,95],
                "delete_item":true}
                """, BargainData.class);
        assertEquals(1013, row.getQuestId());
        assertEquals(100496, row.getItemId());
        assertEquals(List.of(15000, 20000), row.getExpectedValue());
        assertEquals(List.of(85, 95), row.getRandomMood());
        assertTrue(row.isDeleteItem());
        var old = JsonUtils.decode(
                "{\"id\":2,\"questId\":1013,\"itemId\":100496}",
                BargainData.class);
        assertEquals(1013, old.getQuestId());
        assertEquals(100496, old.getItemId());
    }
}