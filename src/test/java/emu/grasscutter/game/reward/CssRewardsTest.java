package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.ItemIdRegistry;
import emu.grasscutter.game.drop.YamlLootTables;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CssRewardsTest {
    private final ItemIdRegistry items = new ItemIdRegistry(Map.of(
            102, "Adventure EXP",
            201, "Primogem",
            202, "Mora",
            104003, "Hero's Wit"));

    private final YamlLootTables loot = YamlLootTables.parse("""
            currency:
              - {reward: genshin.currency.mora, count: 100}
            经验书:
              - {reward: genshin.avatar_up.heros_wit}
            surprise:
              - {reward: genshin.currency.mora, count: 30}
              - {reward: genshin.avatar_up.heros_wit}
            """, items::resolve);

    @Test
    void dottedResourcePropertiesCoexistWithNativeCssCascade() {
        var css = CssRewards.parse("""
                chest {
                    --grant-genshin\\.currency\\.mora: "1000~1200";
                    --grant-astaps\\.loot\\.currency: 1;
                }
                chest[grade="common"] {
                    --grant-genshin\\.currency\\.mora: 500;
                    --grant-astaps\\.loot\\.currency: 2;
                }
                """, items, loot);
        var base = css.resolve("chest", Map.of());
        assertTrue(base.rangedItems().containsKey(202));
        assertEquals(Map.of("astaps.loot.currency", 1), base.loot());
        var common = css.resolve("chest", Map.of("grade", "common"));
        assertEquals(Map.of(202, 500), common.items());
        assertEquals(Map.of("astaps.loot.currency", 2), common.loot());
        assertFalse(common.rangedItems().containsKey(202));
        assertEquals(500, RewardRoller.roll(common, loot, new Random(7)).get(202));
    }

    @Test
    void hierarchicalGemResourceAliasesWorkInCss() {
        var registry = new ItemIdRegistry(Map.of(8001, "Vayuda Turquoise Sliver"));
        var css = CssRewards.parse("""
                boss {
                    --grant-genshin\\.avatar_ascension\\.anemo\\.sliver: 3;
                }
                boss[tag="special"] {
                    --grant-genshin\\.avatar_ascension\\.vayuda_turquoise\\.sliver: 5;
                }
                """, registry, loot);
        assertEquals(3, css.resolve("boss", Map.of()).items().get(8001));
        assertEquals(5, css.resolve("boss", Map.of("tag", "special")).items().get(8001));
    }

    @Test
    void numericGradeAliasesResolveThroughCssAndShareCascadeIdentity() {
        var items = new ItemIdRegistry(Map.of(
                8101, "Vayuda Turquoise Sliver",
                8102, "Vayuda Turquoise Gemstone",
                8201, "Guide to Freedom"));
        var css = CssRewards.parse("""
                boss {
                    --grant-genshin\\.avatar_ascension\\.anemo\\.2: 1;
                    --grant-genshin\\.avatar_ascension\\.anemo\\.5: 2;
                    --grant-genshin\\.talent_up\\.freedom\\.3: 3;
                }
                boss[tag="override"] {
                    --grant-genshin\\.avatar_ascension\\.vayuda_turquoise\\.sliver: 4;
                    --grant-genshin\\.talent_up\\.freedom\\.guide: 5;
                }
                """, items, loot);
        assertEquals(Map.of(8101, 1, 8102, 2, 8201, 3),
                css.resolve("boss", Map.of()).items());
        assertEquals(Map.of(8101, 4, 8102, 2, 8201, 5),
                css.resolve("boss", Map.of("tag", "override")).items());
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse(
                "boss { --grant-genshin\\.talent_up\\.freedom\\.5: 1; }", items, loot));
    }

    @Test
    void importantOverridesMoreSpecificAndLaterNormalDeclarations() {
        var css = CssRewards.parse("""
                chest {
                    --grant-genshin\\.currency\\.mora: 8 !important;
                    --grant-astaps\\.loot\\.currency: 1 !important;
                }
                chest[city="1"][grade="common"] {
                    --grant-genshin\\.currency\\.mora: 42;
                    --grant-astaps\\.loot\\.currency: 3;
                    --grant-genshin\\.currency\\.primogem: 5 !important;
                }
                chest[city="1"] {
                    --grant-genshin\\.currency\\.primogem: 2 !important;
                }
                """, items, loot);
        var rewards = css.resolve("chest", Map.of("city", "1", "grade", "common"));
        assertEquals(8, rewards.items().get(202));
        assertEquals(5, rewards.items().get(201));
        assertEquals(1, rewards.loot().get("astaps.loot.currency"));
    }

    @Test
    void hexEscapedCssIdentifiersResolveWithLibraryParser() {
        var css = CssRewards.parse(
                "chest { --grant-genshin\\\\2e currency\\\\2e mora: 4; }", items, loot);
        assertEquals(Map.of(202, 4), css.resolve("chest", Map.of()).items());
    }

    @Test
    void obsoleteHyphenatedCssResourceNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                CssRewards.parse("chest { --genshin-currency-mora: 1; }", items, loot));
        assertThrows(IllegalArgumentException.class, () ->
                CssRewards.parse("chest { --astaps-loot-currency: 1; }", items, loot));
    }

    @Test
    void talentBookFamiliesAreIndependentCssRewards() {
        var bookItems = new ItemIdRegistry(Map.of(
                7201, "Teachings of Freedom",
                7202, "Guide to Freedom",
                7203, "Philosophies of Freedom"));
        var css = CssRewards.parse("""
                chest {
                    --grant-genshin\\.talent_up\\.freedom\\.teachings: 1;
                    --grant-genshin\\.talent_up\\.freedom\\.guide: 2;
                    --grant-genshin\\.talent_up\\.freedom\\.philosophies: 3;
                }
                """, bookItems, loot);
        assertEquals(Map.of(7201, 1, 7202, 2, 7203, 3),
                css.resolve("chest", Map.of()).items());
        assertThrows(IllegalArgumentException.class, () -> CssRewards.parse(
                "chest { --grant-genshin\\.talent_up\\.teachings_of_freedom: 1; }",
                bookItems, loot));
    }

    @Test
    void inventoryRewardsSupportBonusCountsAndFixedCascades() {
        var css = CssRewards.parse("""
                chest {
                    --grant-genshin\\.currency\\.mora: '1:57.11,2:42.89';
                }
                chest[grade="common"] {
                    --grant-genshin\\.currency\\.mora: 3;
                }
                """, items, loot);
        var ranged = css.resolve("chest", Map.of());
        assertTrue(ranged.items().isEmpty());
        assertTrue(ranged.rangedItems().containsKey(202));
        var random = new Random(71);
        boolean one = false, two = false;
        for (int i = 0; i < 300; i++) {
            int count = RewardRoller.roll(ranged, loot, random).get(202);
            assertTrue(count == 1 || count == 2);
            one |= count == 1;
            two |= count == 2;
        }
        assertTrue(one && two);
        var fixed = css.resolve("chest", Map.of("grade", "common"));
        assertEquals(3, fixed.items().get(202));
        assertFalse(fixed.rangedItems().containsKey(202));
        assertEquals(3, RewardRoller.roll(fixed, loot, new Random(71)).get(202));
    }

    @Test
    void weaponPropertiesResolveToNativeWeaponItems() {
        var registry = new ItemIdRegistry(Map.of(11101, "Dull Blade", 202, "Mora"));
        var css = CssRewards.parse("""
                chest { --grant-genshin\\.weapon\\.dull_blade: 1; }
                """, registry, loot);
        assertEquals(1, css.resolve("chest", Map.of()).items().get(11101));
        assertThrows(IllegalArgumentException.class, () ->
                CssRewards.parse("chest { --grant-genshin\\.material\\.dull_blade: 1; }", registry, loot));
        assertThrows(IllegalArgumentException.class, () ->
                CssRewards.parse("chest { --grant-genshin\\.avatar\\.amber: 1; }", registry, loot));
        assertThrows(IllegalArgumentException.class, () ->
                CssRewards.parse("chest { --grant-genshin\\.recipe\\.mora_meat: 1; }", registry, loot));
    }

    @Test
    void recipeItemRewardsUseRecipeNamespaceButCannotGrantRecipeDefinitions() {
        var registry = new ItemIdRegistry(Map.of(109406, "Recipe: Mora Meat"));
        var css = CssRewards.parse("""
                chest { --grant-genshin\\.recipe\\.mora_meat\\.item: 1; }
                """, registry, loot);
        assertEquals(1, css.resolve("chest", Map.of()).items().get(109406));
        assertThrows(IllegalArgumentException.class, () ->
                CssRewards.parse("chest { --grant-genshin\\.recipe\\.mora_meat: 1; }", registry, loot));
        assertThrows(IllegalArgumentException.class, () ->
                CssRewards.parse("chest { --grant-genshin\\.material\\.recipe_mora_meat: 1; }",
                        registry, loot));
    }

    @Test
    void commaGroupedLevelRangesKeepIndependentSelectors() {
        var css = CssRewards.parse("""
                boss[type="world"][data-range-level-93-99],
                boss[type="world"][data-range-level-100-104],
                boss[type="weekly"][data-range-level-90-up] {
                    --grant-genshin\\.currency\\.mora: 6000;
                }
                boss[type="world"][tag="无相之雷"][data-range-level-93-99] {
                    --grant-genshin\\.currency\\.primogem: 2;
                }
                """, items, loot);
        assertEquals(6000, css.resolve("boss", Map.of(
                "type", "world", "level", "93")).items().get(202));
        assertEquals(6000, css.resolve("boss", Map.of(
                "type", "world", "level", "104")).items().get(202));
        assertEquals(6000, css.resolve("boss", Map.of(
                "type", "weekly", "level", "105")).items().get(202));
        assertTrue(css.resolve("boss", Map.of(
                "type", "world", "level", "105")).items().isEmpty());
        var tagged = css.resolve("boss", Map.of(
                "type", "world", "tag", "无相之雷", "level", "95"));
        assertEquals(6000, tagged.items().get(202));
        assertEquals(2, tagged.items().get(201));
    }

    @Test
    void independentNamespacedLootPropertiesCanSelectSeveralTables() {
        var css = CssRewards.parse("""
                chest[grade="common"] {
                    --grant-astaps\\.loot\\.currency: 1;
                    --grant-astaps\\.loot\\.经验书: 2;
                    --grant-genshin\\.currency\\.primogem: 2;
                }
                """, items, loot);
        var selected = css.resolve("chest", Map.of("grade", "common"));
        assertEquals(Map.of("astaps.loot.currency", 1, "astaps.loot.经验书", 2),
                selected.loot());
        assertEquals(Map.of(201, 2), selected.items());
        assertEquals(Map.of(202, 100, 104003, 2, 201, 2),
                RewardRoller.roll(selected, loot, new Random(1)));
    }

    @Test
    void aQualifiedLootPropertySupportsExecutionCounts() {
        var css = CssRewards.parse("""
                chest { --grant-astaps\\.loot\\.surprise: 3; }
                """, items, loot);
        assertEquals(Map.of("astaps.loot.surprise", 3),
                css.resolve("chest", Map.of()).loot());
        assertEquals(Map.of(202, 90, 104003, 3),
                RewardRoller.roll(css.resolve("chest", Map.of()), loot, new Random(1)));
    }

    @Test
    void lootPropertiesCascadeIndependentlyRatherThanReplacingAList() {
        var css = CssRewards.parse("""
                chest[grade="common"] {
                    --grant-astaps\\.loot\\.currency: 1;
                    --grant-astaps\\.loot\\.经验书: 1;
                    --grant-genshin\\.currency\\.primogem: 2;
                }
                chest[grade="common"][level="6"] {
                    --grant-astaps\\.loot\\.currency: 0;
                    --grant-astaps\\.loot\\.surprise: 2;
                }
                """, items, loot);
        assertEquals(Map.of("astaps.loot.currency", 1, "astaps.loot.经验书", 1),
                css.resolve("chest", Map.of("grade", "common", "level", "1")).loot());
        var selected = css.resolve("chest", Map.of("grade", "common", "level", "6"));
        assertEquals(Map.of(
                "astaps.loot.currency", 0,
                "astaps.loot.经验书", 1,
                "astaps.loot.surprise", 2), selected.loot());
        assertEquals(Map.of(202, 60, 104003, 3, 201, 2),
                RewardRoller.roll(selected, loot, new Random(1)));
    }

    @Test
    void zeroDisablesOneLootPropertyWithoutClearingItems() {
        var css = CssRewards.parse("""
                chest { --grant-astaps\\.loot\\.currency: 1; --grant-genshin\\.currency\\.primogem: 2; }
                chest[level="6"] { --grant-astaps\\.loot\\.currency: 0; }
                """, items, loot);
        var selected = css.resolve("chest", Map.of("level", "6"));
        assertEquals(Map.of("astaps.loot.currency", 0), selected.loot());
        assertEquals(Map.of(201, 2), RewardRoller.roll(selected, loot, new Random(1)));
        assertThrows(IllegalStateException.class, () ->
                RewardConfiguration.requireChestLoot("chest", selected,
                        Map.of("level", "6")));
    }

    @Test
    void tablesCanBeUsedByUnrelatedObjects() {
        var css = CssRewards.parse("""
                monster[rank="elite"] { --grant-astaps\\.loot\\.currency: 1; }
                chest[grade="common"] { --grant-astaps\\.loot\\.currency: 3; }
                """, items, loot);
        assertEquals(Map.of(202, 100),
                RewardRoller.roll(css.resolve("monster", Map.of("rank", "elite")),
                        loot, new Random(1)));
        assertEquals(Map.of(202, 300),
                RewardRoller.roll(css.resolve("chest", Map.of("grade", "common")),
                        loot, new Random(1)));
    }

    @Test
    void fixedItemQuantitiesOverrideRolledTotals() {
        var css = CssRewards.parse("""
                chest {
                    --grant-astaps\\.loot\\.currency: 1;
                    --grant-astaps\\.loot\\.经验书: 1;
                    --grant-genshin\\.currency\\.mora: 25;
                    --grant-genshin\\.currency\\.primogem: 2;
                }
                chest[scene="3"] { --grant-genshin\\.currency\\.mora: 0; }
                """, items, loot);
        assertEquals(Map.of(202, 25, 104003, 1, 201, 2),
                RewardRoller.roll(css.resolve("chest", Map.of()), loot, new Random(1)));
        assertEquals(Map.of(104003, 1, 201, 2),
                RewardRoller.roll(css.resolve("chest", Map.of("scene", "3")),
                        loot, new Random(1)));
    }

    @Test
    void selectorSpecificityBeatsLaterSourceOrderForSameLootProperty() {
        var css = CssRewards.parse("""
                chest[grade="common"][scene="3"] { --grant-astaps\\.loot\\.surprise: 2; }
                chest[grade="common"] {
                    --grant-astaps\\.loot\\.surprise: 1;
                    --grant-astaps\\.loot\\.currency: 1;
                }
                """, items, loot);
        assertEquals(Map.of("astaps.loot.surprise", 2, "astaps.loot.currency", 1),
                css.resolve("chest", Map.of("grade", "common", "scene", "3")).loot());
    }

    @Test
    void elementSelectorOutranksEqualAttributeCountUniversalSelector() {
        var css = CssRewards.parse("""
                chest[grade="common"] { --grant-astaps\\.loot\\.currency: 1; }
                *[grade="common"] { --grant-astaps\\.loot\\.currency: 2; }
                """, items, loot);
        assertEquals(Map.of("astaps.loot.currency", 1),
                css.resolve("chest", Map.of("grade", "common")).loot());
    }

    @Test
    void chestTypeAndGradeSelectIndependently() {
        var css = CssRewards.parse("""
                chest[type="normal"][grade="common"] { --grant-astaps\\.loot\\.currency: 1; }
                chest[type="mora"] { --grant-astaps\\.loot\\.currency: 2; }
                """, items, loot);
        assertEquals(Map.of("astaps.loot.currency", 1), css.resolve("chest",
                Map.of("type", "normal", "grade", "common")).loot());
        assertEquals(Map.of("astaps.loot.currency", 2), css.resolve("chest",
                Map.of("type", "mora", "grade", "common")).loot());
        assertTrue(css.resolve("chest", Map.of("grade", "common")).loot().isEmpty());
        assertTrue(css.resolve("chest", Map.of("type", "normal", "grade", "luxurious"))
                .loot().isEmpty());
    }

    @Test
    void modernSelectorsUseLibraryMatchingAndSpecificity() {
        var css = CssRewards.parse("""
                chest.special:not([grade="bad"]) { --grant-genshin\\.currency\\.mora: 7; }
                rewards > chest#target { --grant-genshin\\.currency\\.mora: 9; }
                chest:where(#target) { --grant-genshin\\.currency\\.primogem: 3; }
                """, items, loot);
        assertEquals(Map.of(202, 9, 201, 3), css.resolve("chest",
                Map.of("class", "special", "id", "target", "grade", "common")).items());
        assertEquals(Map.of(202, 7), css.resolve("chest",
                Map.of("class", "special", "grade", "common")).items());
        assertTrue(css.resolve("chest", Map.of("grade", "bad")).items().isEmpty());
    }

    @Test
    void isAndWhereFollowSelectorsLevelFourSpecificity() {
        var css = CssRewards.parse("""
                boss:where(#strong) { --grant-genshin\\.currency\\.mora: 90; }
                boss[type="world"] { --grant-genshin\\.currency\\.mora: 20; }
                boss:is(#strong, [tag="weak"]) { --grant-genshin\\.currency\\.mora: 30; }
                """, items, loot);
        assertEquals(30, css.resolve("boss",
                Map.of("id", "strong", "type", "world")).items().get(202));
        assertEquals(30, css.resolve("boss",
                Map.of("tag", "weak", "type", "world")).items().get(202));
    }

    @Test
    void cssNestingUsesLibrarySelectorExpansionAndCascade() {
        var css = CssRewards.parse("""
                boss[type="world"] {
                    --grant-genshin\\.currency\\.mora: 1;
                    &[tag="a"] { --grant-genshin\\.currency\\.mora: 5; }
                    &:is([tag="a"], [tag="b"]) {
                        --grant-genshin\\.currency\\.primogem: 3;
                    }
                    --grant-genshin\\.currency\\.mora: 2;
                }
                """, items, loot);
        assertEquals(Map.of(202, 5, 201, 3), css.resolve("boss",
                Map.of("type", "world", "tag", "a")).items());
        assertEquals(Map.of(202, 2, 201, 3), css.resolve("boss",
                Map.of("type", "world", "tag", "b")).items());
        assertEquals(Map.of(202, 2), css.resolve("boss",
                Map.of("type", "world", "tag", "c")).items());
    }

    @Test
    void repeatedLootPropertyUsesLastDeclaration() {
        var css = CssRewards.parse("""
                chest {
                    --grant-astaps\\.loot\\.currency: 1;
                    --grant-astaps\\.loot\\.currency: 2;
                }
                """, items, loot);
        assertEquals(Map.of("astaps.loot.currency", 2),
                css.resolve("chest", Map.of()).loot());
    }

    @Test
    void malformedAndLegacyLootPropertiesAreRejected() {
        for (String declaration : new String[]{
                "--loot: currency;",
                "--loot: \"astaps.loot.currency\";",
                "--grant-astaps\\.loot\\.missing: 1;",
                "--grant-astaps\\.loot\\.currency: none;",
                "--grant-astaps\\.loot\\.currency: -1;",
                "--grant-genshin\\.loot\\.currency: 1;",
                "--grant-genshin\\.avatar\\.amber: 1;",
                "astaps.item.mora: 1;"}) {
            assertThrows(IllegalArgumentException.class, () ->
                    CssRewards.parse("chest { " + declaration + " }", items, loot),
                    declaration);
        }
    }

    @Test
    void lootExecutionCountsSupportRangesAndPercentageDistributions() {
        var css = CssRewards.parse("""
                chest {
                    --grant-astaps\\.loot\\.currency: "1~3";
                    --grant-astaps\\.loot\\.经验书: "1:50,2:30";
                }
                """, items, loot);
        var selection = css.resolve("chest", Map.of());
        assertTrue(selection.loot().isEmpty());
        assertEquals(2, selection.rangedLoot().size());

        var currencyCounts = new java.util.HashSet<Integer>();
        var bookCounts = new java.util.HashSet<Integer>();
        var random = new Random(71);
        for (int i = 0; i < 300; i++) {
            var rewards = RewardRoller.roll(selection, loot, random);
            currencyCounts.add(rewards.getOrDefault(202, 0) / 100);
            bookCounts.add(rewards.getOrDefault(104003, 0));
        }
        assertEquals(java.util.Set.of(1, 2, 3), currencyCounts);
        assertEquals(java.util.Set.of(1, 2), bookCounts);
    }

    @Test
    void fixedAndVariableLootCountsOverrideOneAnother() {
        var css = CssRewards.parse("""
                chest { --grant-astaps\\.loot\\.currency: "1~3"; }
                chest[level="6"] { --grant-astaps\\.loot\\.currency: 2; }
                chest[level="6"][city="1"] { --grant-astaps\\.loot\\.currency: "0:70,3:30"; }
                """, items, loot);
        var original = css.resolve("chest", Map.of());
        assertTrue(original.loot().isEmpty());
        assertTrue(original.rangedLoot().containsKey("astaps.loot.currency"));

        var fixed = css.resolve("chest", Map.of("level", "6"));
        assertEquals(Map.of("astaps.loot.currency", 2), fixed.loot());
        assertTrue(fixed.rangedLoot().isEmpty());
        assertEquals(200, RewardRoller.roll(fixed, loot, new Random(2)).get(202));

        var variable = css.resolve("chest", Map.of("level", "6", "city", "1"));
        assertTrue(variable.loot().isEmpty());
        assertEquals(1, variable.rangedLoot().size());
        var outcomes = new java.util.HashSet<Integer>();
        var random = new Random(72);
        for (int i = 0; i < 100; i++) {
            outcomes.add(RewardRoller.roll(variable, loot, random).getOrDefault(202, 0));
        }
        assertEquals(java.util.Set.of(0, 300), outcomes);
    }

    @Test
    void quotedLevelRangesCoverSixSourceIntervalsWithoutFallback() {
        var css = CssRewards.parse("""
                chest[grade="common"] { --grant-genshin\\.currency\\.primogem: 2; }
                chest[grade="common"][data-range-level-1-5] { --grant-astaps\\.loot\\.surprise: 1; }
                chest[grade="common"][data-range-level-6-10] { --grant-astaps\\.loot\\.currency: 2; }
                chest[grade="common"][data-range-level-11-15] { --grant-astaps\\.loot\\.surprise: 3; }
                chest[grade="common"][data-range-level-16-20] { --grant-astaps\\.loot\\.currency: 4; }
                chest[grade="common"][data-range-level-21-25] { --grant-astaps\\.loot\\.surprise: 5; }
                chest[grade="common"][data-range-level-26-up] { --grant-astaps\\.loot\\.currency: 6; }
                """, items, loot);
        int[] levels = {1, 5, 6, 10, 11, 15, 16, 20, 21, 25, 26, 99};
        String[] names = {
                "surprise", "surprise", "currency", "currency",
                "surprise", "surprise", "currency", "currency",
                "surprise", "surprise", "currency", "currency"
        };
        int[] times = {1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6};
        for (int i = 0; i < levels.length; i++) {
            assertEquals(Map.of("astaps.loot." + names[i], times[i]),
                    css.resolve("chest", Map.of("grade", "common",
                            "level", Integer.toString(levels[i]))).loot(),
                    "level " + levels[i]);
        }
        for (String level : new String[]{"0", "unknown"}) {
            var attrs = Map.of("grade", "common", "level", level);
            var selection = css.resolve("chest", attrs);
            assertTrue(selection.loot().isEmpty());
            assertEquals(2, selection.items().get(201).intValue());
            assertThrows(IllegalStateException.class, () ->
                    RewardConfiguration.requireChestLoot("chest", selection, attrs));
        }
        assertTrue(css.resolve("chest", Map.of("grade", "common")).loot().isEmpty());
    }

    @Test
    void standardIsSelectorsMatchAttributeAlternatives() {
        var css = CssRewards.parse("""
                chest[type="normal"][grade="common"][city="1"] {
                    --grant-genshin\\.currency\\.primogem: 0;
                }
                chest[type="normal"][grade="common"]:is([area="10"], [area="77"], [area="8"], [area="19"], [area="40"], [area="41"], [area="47"], [area="300"]) {
                    --grant-genshin\\.currency\\.primogem: 2;
                }
                boss[type="world"]:is([tag="electro_hypostasis"], [tag="anemo_hypostasis"]) {
                    --grant-genshin\\.currency\\.mora: 100;
                }
                """, items, loot);
        for (String area : new String[]{"10", "77", "8", "19", "40", "41", "47", "300"}) {
            assertEquals(2, css.resolve("chest", Map.of("type", "normal",
                    "grade", "common", "city", "1", "area", area)).items().get(201));
        }
        assertEquals(0, css.resolve("chest", Map.of("type", "normal",
                "grade", "common", "city", "1", "area", "9")).items().get(201));
        assertEquals(100, css.resolve("boss", Map.of(
                "type", "world", "tag", "electro_hypostasis")).items().get(202));
        assertEquals(100, css.resolve("boss", Map.of(
                "type", "world", "tag", "anemo_hypostasis")).items().get(202));
        assertTrue(css.resolve("boss", Map.of(
                "type", "world", "tag", "geo_hypostasis")).items().isEmpty());
        // Quoted commas are literal attribute values in standard CSS.
        var literal = CssRewards.parse("""
                chest[area="8,10"] { --grant-genshin\\.currency\\.mora: 1; }
                """, items, loot);
        assertEquals(1, literal.resolve("chest", Map.of("area", "8,10"))
                .items().get(202));
        assertTrue(literal.resolve("chest", Map.of("area", "8")).items().isEmpty());
        assertTrue(literal.resolve("chest", Map.of("area", "10")).items().isEmpty());
    }

    @Test
    void isSelectorKeepsCssSpecificityAndLevelRanges() {
        var css = CssRewards.parse("""
                boss[type="world"]:is([tag="a"], [tag="b"])[data-range-level-70-79] {
                    --grant-genshin\\.currency\\.mora: 10;
                }
                boss[type="world"][data-range-level-70-79] {
                    --grant-genshin\\.currency\\.mora: 20;
                }
                boss[type="world"][tag="b"][data-range-level-70-79] {
                    --grant-genshin\\.currency\\.mora: 30;
                }
                """, items, loot);
        assertEquals(10, css.resolve("boss", Map.of(
                "type", "world", "tag", "a", "level", "75")).items().get(202));
        assertEquals(30, css.resolve("boss", Map.of(
                "type", "world", "tag", "b", "level", "75")).items().get(202));
        assertEquals(20, css.resolve("boss", Map.of(
                "type", "world", "tag", "c", "level", "75")).items().get(202));
        assertTrue(css.resolve("boss", Map.of(
                "type", "world", "tag", "a", "level", "80")).items().isEmpty());
    }

    @Test
    void numericRangesApplyToEveryAttributeAndKeepSpecificity() {
        var css = CssRewards.parse("""
                blossom[type="wealth"][data-range-world_level-6-9] {
                    --grant-genshin\\.currency\\.mora: 60000;
                }
                blossom[type="wealth"][world_level="7"][city="1"] {
                    --grant-genshin\\.currency\\.mora: 30000;
                }
                chest[data-range-rank-2-up] {
                    --grant-genshin\\.currency\\.primogem: 5;
                }
                """, items, loot);
        for (String value : new String[]{"6", "7", "8", "9"}) {
            assertEquals(60000, css.resolve("blossom", Map.of(
                    "type", "wealth", "world_level", value)).items().get(202));
        }
        assertEquals(30000, css.resolve("blossom", Map.of(
                "type", "wealth", "world_level", "7", "city", "1")).items().get(202));
        for (String value : new String[]{"5", "10", "unknown"}) {
            assertTrue(css.resolve("blossom", Map.of(
                    "type", "wealth", "world_level", value)).items().isEmpty());
        }
        assertTrue(css.resolve("blossom", Map.of("type", "wealth")).items().isEmpty());
        assertTrue(css.resolve("chest", Map.of("rank", "1")).items().isEmpty());
        assertEquals(5, css.resolve("chest", Map.of("rank", "2")).items().get(201));
        assertEquals(5, css.resolve("chest", Map.of("rank", "999")).items().get(201));
    }

    @Test
    void levelRangesPreserveSpecificityAndExactEquality() {
        var css = CssRewards.parse("""
                chest[data-range-level-6-10] { --grant-astaps\\.loot\\.surprise: 2; }
                chest[data-range-level-11-16] { --grant-astaps\\.loot\\.currency: 3; }
                chest[grade="common"][data-range-level-16-20] { --grant-astaps\\.loot\\.surprise: 4; }
                chest[level="30"] { --grant-astaps\\.loot\\.currency: 5; }
                """, items, loot);
        assertTrue(css.resolve("chest", Map.of("level", "5")).loot().isEmpty());
        assertEquals(Map.of("astaps.loot.surprise", 2),
                css.resolve("chest", Map.of("level", "6")).loot());
        assertEquals(Map.of("astaps.loot.surprise", 2),
                css.resolve("chest", Map.of("level", "10")).loot());
        assertEquals(Map.of("astaps.loot.currency", 3),
                css.resolve("chest", Map.of("level", "11")).loot());
        assertEquals(Map.of("astaps.loot.currency", 3, "astaps.loot.surprise", 4),
                css.resolve("chest", Map.of("grade", "common", "level", "16")).loot());
        assertTrue(css.resolve("chest", Map.of("grade", "common", "level", "21"))
                .loot().isEmpty());
        assertEquals(Map.of("astaps.loot.currency", 5),
                css.resolve("chest", Map.of("level", "30")).loot());
    }

    @Test
    void rangeMembershipAttributesValidateBoundsAndPreserveLiteralEquality() {
        for (String attr : new String[]{"level", "WR", "world_level", "rank"}) {
            assertThrows(IllegalArgumentException.class, () ->
                    CssRewards.parse("chest[data-range-" + attr
                            + "-10-5] { --grant-astaps\\.loot\\.currency: 1; }", items, loot));
        }
        var css = CssRewards.parse("""
                chest[level="6~10"] { --grant-genshin\\.currency\\.mora: 9; }
                chest[data-range-level-6-10] { --grant-genshin\\.currency\\.primogem: 2; }
                """, items, loot);
        assertEquals(Map.of(202, 9), css.resolve("chest", Map.of("level", "6~10")).items());
        assertEquals(Map.of(201, 2), css.resolve("chest", Map.of("level", "7")).items());
        assertTrue(css.resolve("chest", Map.of("level", "11")).items().isEmpty());
        assertTrue(css.resolve("chest", Map.of()).items().isEmpty());
    }

    @Test
    void whereSelectorsHaveZeroSpecificityAndMatchNatively() {
        var css = CssRewards.parse("""
                boss[type="world"][tag="a"] {
                    --grant-genshin\\.currency\\.mora: 20;
                }
                boss[type="world"]:where([tag="a"], [tag="b"]) {
                    --grant-genshin\\.currency\\.mora: 10;
                }
                boss[type="world"]:is([tag="a"], [tag="b"]) {
                    --grant-genshin\\.currency\\.primogem: 3;
                }
                """, items, loot);
        assertEquals(Map.of(202, 20, 201, 3), css.resolve("boss",
                Map.of("type", "world", "tag", "a")).items());
        assertEquals(Map.of(202, 10, 201, 3), css.resolve("boss",
                Map.of("type", "world", "tag", "b")).items());
        assertTrue(css.resolve("boss", Map.of("type", "world", "tag", "c")).items().isEmpty());
    }

    @Test
    void numericMembershipSupportsOverlappingOpenRangesAndLargeValues() {
        var css = CssRewards.parse("""
                boss[type="world"][data-range-level-70-79] {
                    --grant-genshin\\.currency\\.mora: 70;
                }
                boss[type="world"][data-range-level-79-up] {
                    --grant-genshin\\.currency\\.primogem: 79;
                }
                boss[type="world"][data-range-level-93-up] {
                    --grant-genshin\\.currency\\.mora: 93;
                }
                """, items, loot);
        assertEquals(Map.of(202, 70), css.resolve("boss",
                Map.of("type", "world", "level", "70")).items());
        assertEquals(Map.of(202, 70, 201, 79), css.resolve("boss",
                Map.of("type", "world", "level", "79")).items());
        assertEquals(Map.of(201, 79), css.resolve("boss",
                Map.of("type", "world", "level", "80")).items());
        assertEquals(Map.of(202, 93, 201, 79), css.resolve("boss",
                Map.of("type", "world", "level", "93")).items());
        assertEquals(Map.of(202, 93, 201, 79), css.resolve("boss",
                Map.of("type", "world", "level", "999999999999999999999999999")).items());
        assertTrue(css.resolve("boss", Map.of("type", "world", "level", "unknown")).items().isEmpty());
        assertTrue(css.resolve("boss", Map.of("type", "world")).items().isEmpty());
        assertTrue(css.resolve("boss", Map.of("type", "world", "level", "-1")).items().isEmpty());
    }

    @Test
    void rangedCurrencyRollsInclusivelyAndOverridesLoot() {
        var css = CssRewards.parse("""
                chest {
                    --grant-astaps\\.loot\\.currency: 1;
                    --grant-genshin\\.currency\\.mora: "3~5";
                }
                """, items, loot);
        var selected = css.resolve("chest", Map.of());
        var observed = new java.util.HashSet<Integer>();
        var random = new Random(214);
        for (int i = 0; i < 100; i++) {
            int mora = RewardRoller.roll(selected, loot, random).get(202);
            assertTrue(mora >= 3 && mora <= 5);
            observed.add(mora);
        }
        assertEquals(java.util.Set.of(3, 4, 5), observed);
    }

    @Test
    void fixedAndRangedCurrencyRespectCascadePrecedence() {
        var css = CssRewards.parse("""
                chest { --grant-genshin\\.currency\\.mora: "3~5"; }
                chest[city="1"] { --grant-genshin\\.currency\\.mora: 9; }
                chest[city="1"][area="2"] { --grant-genshin\\.currency\\.mora: "11~12"; }
                """, items, loot);
        assertEquals(9, RewardRoller.roll(
                css.resolve("chest", Map.of("city", "1")), loot, new Random(1))
                .get(202).intValue());
        int value = RewardRoller.roll(css.resolve("chest",
                Map.of("city", "1", "area", "2")), loot, new Random(2)).get(202);
        assertTrue(value == 11 || value == 12);
    }
}
