package emu.grasscutter.game.entity.gadget.chest;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.entity.gadget.chest.StandardChestClassifier.Classification;
import emu.grasscutter.game.entity.gadget.chest.StandardChestClassifier.Exclusion;
import emu.grasscutter.game.entity.gadget.chest.StandardChestClassifier.Kind;
import emu.grasscutter.game.reward.ChestRewardConfig;
import emu.grasscutter.game.reward.RewardConfig;
import emu.grasscutter.game.reward.StandardChestRewardResolver;
import emu.grasscutter.game.world.WorldAreaIndex.AreaIds;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorldChestLootHelperPolicyTest {
    private static RewardConfig config() throws IOException {
        try (var stream = RewardConfig.class.getResourceAsStream("/rewards.css")) {
            assertNotNull(stream);
            return RewardConfig.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static Map<Integer,Integer> regional(Kind kind, int scene, int area1, int area2)
            throws IOException {
        return StandardChestRewardResolver.resolve(
                new Classification(kind, Exclusion.NONE), scene,
                new AreaIds(area1, area2), config().chest()).orElseThrow().amounts();
    }

    @Test
    void commonLowGemsAreZeroWhileOtherFixedItemsComeFromCss() throws IOException {
        var low = ChestRewardConfig.resolveRegionalSigils(regional(Kind.COMMON,3,1,0),305);
        assertEquals(Map.of(102,30,201,0),low);
        assertEquals(2, ChestRewardConfig.resolveRegionalSigils(
                regional(Kind.COMMON,3,10,0),305).get(201));
        assertEquals(2, ChestRewardConfig.resolveRegionalSigils(
                regional(Kind.COMMON,3,1,104),305).get(201));
    }

    @Test
    void allFourKindsResolveQuantitiesExclusivelyFromCss() throws IOException {
        var cfg = config().chest();
        assertEquals(Map.of(102,30,201,2),
                ChestRewardConfig.resolveRegionalSigils(
                        cfg.resolve(ChestRewardConfig.Kind.COMMON,0,0,0).amounts(),305));
        assertEquals(Map.of(102,30,201,5),
                ChestRewardConfig.resolveRegionalSigils(
                        cfg.resolve(ChestRewardConfig.Kind.EXQUISITE,0,0,0).amounts(),307));
        assertEquals(Map.of(102,30,201,10),
                ChestRewardConfig.resolveRegionalSigils(
                        cfg.resolve(ChestRewardConfig.Kind.PRECIOUS,0,0,0).amounts(),303));
        assertEquals(Map.of(102,30,201,10),
                ChestRewardConfig.resolveRegionalSigils(
                        cfg.resolve(ChestRewardConfig.Kind.LUXURIOUS,0,0,0).amounts(),301));
    }

    @Test
    void explicitItemOverridesSigilsWithoutDuplicateDrops() throws IOException {
        var base = new java.util.HashMap<>(
                config().chest().resolve(ChestRewardConfig.Kind.COMMON,0,0,0).amounts());
        base.put(104001,7);
        base.put(305,0);
        base.put(-1,8);
        var result = ChestRewardConfig.resolveRegionalSigils(base,305);
        assertEquals(7,result.get(104001));
        assertEquals(0,result.get(305));
        assertFalse(result.containsKey(-1));
        assertEquals(0,ChestRewardConfig.resolveRegionalSigils(
                Map.of(-1,0,201,3),303).get(303));
        assertEquals(Map.of(201,3),ChestRewardConfig.resolveRegionalSigils(
                Map.of(-1,8,201,3),0));
    }

    @Test
    void tierIsClassificationOnlyAndHasNoNumericPayoutTable() {
        assertEquals(4, WorldChestLootHelper.Tier.values().length);
        for (var tier : WorldChestLootHelper.Tier.values()) {
            assertEquals(tier.name(),ChestRewardConfig.Kind.valueOf(tier.name()).name());
        }
    }
}
