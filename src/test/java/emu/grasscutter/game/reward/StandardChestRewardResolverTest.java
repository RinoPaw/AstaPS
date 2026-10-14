package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.entity.gadget.chest.StandardChestClassifier.Classification;
import emu.grasscutter.game.entity.gadget.chest.StandardChestClassifier.Exclusion;
import emu.grasscutter.game.entity.gadget.chest.StandardChestClassifier.Kind;
import emu.grasscutter.game.world.WorldAreaIndex.AreaIds;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StandardChestRewardResolverTest {
    private static final ChestRewardConfig RULES = ChestRewardConfig.parse("""
            chest[kind="common"] { adventureExp: 30; primogems: 2; }
            chest[kind="exquisite"] { adventureExp: 30; primogems: 5; }
            chest[kind="precious"] { adventureExp: 30; primogems: 10; }
            chest[kind="luxurious"] { adventureExp: 30; primogems: 10; }
            chest[kind="common"][scene="3"][area1="1"] { primogems: 0; }
            chest[kind="common"][scene="3"][area1="1"][area2="104"] { primogems: 2; }
            chest[kind="exquisite"][scene="3"][area1="1"] { primogems: 2; }
            """);

    private static Classification standard(Kind kind) {
        return new Classification(kind, Exclusion.NONE);
    }

    private static Map<Integer, Integer> rewards(
            StandardChestRewardResolver.Preview preview) {
        return preview.rewards().stream()
                .collect(Collectors.toMap(ItemParamData::getId, ItemParamData::getCount));
    }

    @Test
    void appliesSpecificRegionRulesWithoutDoubleMergingItems() {
        var common = standard(Kind.COMMON);
        var high = StandardChestRewardResolver.resolve(
                common, 3, new AreaIds(1, 104), RULES).orElseThrow();
        assertEquals(Map.of(102, 30, 201, 2), rewards(high));
        assertEquals("chest[kind=\"common\"][scene=\"3\"][area1=\"1\"][area2=\"104\"]", high.lastSelector());

        var low = StandardChestRewardResolver.resolve(
                common, 3, new AreaIds(1, 107), RULES).orElseThrow();
        assertEquals(Map.of(102, 30), rewards(low));
        assertEquals("chest[kind=\"common\"][scene=\"3\"][area1=\"1\"]", low.lastSelector());

        var other = StandardChestRewardResolver.resolve(
                common, 3, new AreaIds(10, 1506), RULES).orElseThrow();
        assertEquals(Map.of(102, 30, 201, 2), rewards(other));
        assertEquals("chest[kind=\"common\"]", other.lastSelector());
    }

    @Test
    void missingChildAreaStillAllowsKnownParentOverride() {
        var result = StandardChestRewardResolver.resolve(
                standard(Kind.EXQUISITE), 3, new AreaIds(1, 0), RULES).orElseThrow();
        assertEquals(Map.of(102, 30, 201, 2), rewards(result));
        assertEquals(new AreaIds(1, 0), result.area());
    }

    @Test
    void missingParentAreaCannotSilentlyUseHighPrimogemDefaults() {
        for (AreaIds unresolved : new AreaIds[] {null, new AreaIds(0, 0)}) {
            assertTrue(StandardChestRewardResolver.resolve(
                    standard(Kind.PRECIOUS), 3, unresolved, RULES).isEmpty());
        }
        assertTrue(StandardChestRewardResolver.resolve(
                standard(Kind.PRECIOUS), 0, new AreaIds(1, 104), RULES).isEmpty());
    }

    @Test
    void excludedSpecialChestsNeverReceiveAConfigPreview() {
        for (Exclusion reason : new Exclusion[] {
                Exclusion.SPECIAL_SOURCE,
                Exclusion.BOSS_FLOWER,
                Exclusion.EXPLICIT_DROP,
                Exclusion.QUALITY_CONFLICT,
                Exclusion.UNVERIFIED_SOURCE,
                Exclusion.NOT_OPEN_WORLD
        }) {
            var excluded = new Classification(null, reason);
            assertTrue(StandardChestRewardResolver.resolve(
                    excluded, 3, new AreaIds(1, 104), RULES).isEmpty(), reason.name());
        }
        assertTrue(StandardChestRewardResolver.resolve(
                null, 3, new AreaIds(1, 104), RULES).isEmpty());
    }

    @Test
    void allFourChestKindsMapWithoutGuessingTierValues() {
        for (Kind kind : Kind.values()) {
            var preview = StandardChestRewardResolver.resolve(
                    standard(kind), 3, new AreaIds(10, 0), RULES).orElseThrow();
            assertEquals(kind.name(), preview.kind().name());
        }
    }
}
