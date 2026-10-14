package emu.grasscutter.game.entity.gadget.chest;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.entity.gadget.chest.StandardChestClassifier.Classification;
import emu.grasscutter.game.entity.gadget.chest.StandardChestClassifier.Exclusion;
import emu.grasscutter.game.entity.gadget.chest.StandardChestClassifier.Kind;
import emu.grasscutter.game.reward.RewardConfig;
import emu.grasscutter.game.reward.StandardChestRewardResolver;
import emu.grasscutter.game.world.WorldAreaIndex.AreaIds;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorldChestLootHelperPolicyTest {
    private static Map<Integer, Integer> rulesFor(Kind kind, int scene, int area1, int area2) throws IOException {
        try (InputStream stream = RewardConfig.class.getResourceAsStream("/rewards.css")) {
            assertNotNull(stream);
            var config = RewardConfig.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            return StandardChestRewardResolver.resolve(
                    new Classification(kind, Exclusion.NONE), scene, new AreaIds(area1, area2),
                    config.chest()).orElseThrow().amounts();
        }
    }

    @Test
    void standardCommonChestUsesCssZeroInsteadOfLegacy66() throws IOException {
        var low = WorldChestLootHelper.composeRewards(
                WorldChestLootHelper.Tier.COMMON, 305,
                rulesFor(Kind.COMMON, 3, 1, 0));
        assertEquals(0, low.get(201));
        assertEquals(30, low.get(102));
        assertEquals(1000, low.get(202));
        assertEquals(3, low.get(305));
        assertEquals(2, low.get(104011));

        var exceptional = WorldChestLootHelper.composeRewards(
                WorldChestLootHelper.Tier.COMMON, 305,
                rulesFor(Kind.COMMON, 3, 1, 104));
        assertEquals(2, exceptional.get(201));

        var high = WorldChestLootHelper.composeRewards(
                WorldChestLootHelper.Tier.COMMON, 305,
                rulesFor(Kind.COMMON, 3, 10, 0));
        assertEquals(2, high.get(201));
    }

    @Test
    void cssAmountsReplaceExistingMaterialWithoutDoubleGranting() {
        var values = WorldChestLootHelper.composeRewards(
                WorldChestLootHelper.Tier.COMMON, 305,
                Map.of(102, 45, 201, 0, 104011, 0, 104001, 5, 104004, 4));
        assertEquals(45, values.get(102));
        assertEquals(0, values.get(201));
        assertEquals(0, values.get(104011));
        assertEquals(5, values.get(104001));
        assertEquals(4, values.get(104004));
        assertEquals(1000, values.get(202));
    }

    @Test
    void unverifiedAndSpecialChestFallbackDoesNotGrant66OrMore() {
        for (var tier : WorldChestLootHelper.Tier.values()) {
            var values = WorldChestLootHelper.composeRewards(tier, 305, Map.of());
            assertTrue(values.get(201) <= 10, tier.name());
            assertEquals(30, values.get(102), tier.name());
        }
    }
}
