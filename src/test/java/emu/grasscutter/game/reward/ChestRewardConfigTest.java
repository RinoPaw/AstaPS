package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.reward.ChestRewardConfig.Kind;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class ChestRewardConfigTest {
    private static final String RULES = """
            chest[kind="common"] {
                adventureExp: 30;
                primogems: 2;
            }
            chest[kind="exquisite"] { adventureExp: 30; primogems: 5; }
            chest[kind="precious"] { adventureExp: 30; primogems: 10; }
            chest[kind="luxurious"] { adventureExp: 30; primogems: 10; }
            """;

    private static Map<Integer,Integer> values(ChestRewardConfig.Match match) {
        return match.rewards().stream().collect(
                Collectors.toMap(ItemParamData::getId, ItemParamData::getCount));
    }

    private static Map<Integer,Integer> fixedOnly(ChestRewardConfig.Match match) {
        return values(match).entrySet().stream()
                .filter(e -> e.getKey() == 102 || e.getKey() == 201)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Test
    void bundledDefaultsAndRegionalExceptionsMatchTheOldNumbers() throws Exception {
        String css;
        try (var in = ChestRewardConfig.class.getResourceAsStream("/rewards.css")) {
            assertNotNull(in);
            css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var cfg = ChestRewardConfig.parse(css);
        assertEquals(Map.of(102, 30, 201, 2), fixedOnly(cfg.resolve(Kind.COMMON, 3, 10, 1501)));
        assertEquals(Map.of(102, 30, 201, 5), fixedOnly(cfg.resolve(Kind.EXQUISITE, 3, 8, 801)));
        assertEquals(Map.of(102, 30, 201, 10), fixedOnly(cfg.resolve(Kind.PRECIOUS, 6, 300, 0)));
        assertEquals(Map.of(102, 30, 201, 10), fixedOnly(cfg.resolve(Kind.LUXURIOUS, 3, 2, 201)));
        for (int area : List.of(1, 2, 3, 4, 5, 6, 7, 9)) {
            assertEquals(Map.of(102, 30), fixedOnly(cfg.resolve(Kind.COMMON, 3, area, 0)));
            assertEquals(Map.of(102, 30, 201, 2), fixedOnly(cfg.resolve(Kind.EXQUISITE, 3, area, 0)));
            assertEquals(Map.of(102, 30, 201, 5), fixedOnly(cfg.resolve(Kind.PRECIOUS, 3, area, 0)));
        }
        assertEquals(Map.of(102, 30, 201, 2), fixedOnly(cfg.resolve(Kind.COMMON, 3, 1, 104)));
        assertEquals(Map.of(102, 30, 201, 2), fixedOnly(cfg.resolve(Kind.COMMON, 3, 7, 703)));
        assertEquals(Map.of(102, 30, 201, 5), fixedOnly(cfg.resolve(Kind.EXQUISITE, 3, 1, 104)));
        assertEquals(Map.of(102, 30, 201, 10), fixedOnly(cfg.resolve(Kind.PRECIOUS, 3, 1, 104)));
        assertEquals(Map.of(102, 30, 201, 5), fixedOnly(cfg.resolve(Kind.EXQUISITE, 3, 7, 703)));
        assertEquals(Map.of(102, 30, 201, 10), fixedOnly(cfg.resolve(Kind.PRECIOUS, 3, 7, 703)));
        assertEquals(Map.of(102, 30), fixedOnly(cfg.resolve(Kind.COMMON, 3, 1, 0)));
        assertEquals("chest[kind=\"common\"][scene=\"3\"][area1=\"1\"][area2=\"104\"]",
                cfg.resolve(Kind.COMMON, 3, 1, 104).selector());
    }


    @Test
    void lunaIAndLaterRequireThirtyAdventureExpInAllStandardGrades() throws Exception {
        String bundled;
        try (var in = ChestRewardConfig.class.getResourceAsStream("/rewards.css")) {
            assertNotNull(in);
            bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var config = ChestRewardConfig.parse(bundled);
        for (var kind : Kind.values()) {
            assertEquals(30, config.resolve(kind, 0, 0, 0).amounts().get(102),
                    kind + " must use the Luna I / 7.1 fixed reward");
        }
    }

    @Test
    void missingNativePoolIsExplicitAndNeverEntersOrdinaryNativeRewards() throws Exception {
        String css;
        try (var in = ChestRewardConfig.class.getResourceAsStream("/rewards.css")) {
            assertNotNull(in);
            css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var config = ChestRewardConfig.parse(css);
        for (Kind kind : Kind.values()) {
            var nativeValues = config.resolve(kind, 3, 10, 0).amounts();
            assertEquals(30, nativeValues.get(102));
            assertFalse(nativeValues.containsKey(202));
            assertFalse(nativeValues.containsKey(-1));
            var missingValues = config.resolve(kind, 3, 10, 0, true).amounts();
            assertEquals(30, missingValues.get(102));
            assertTrue(missingValues.get(202) > 0);
            assertTrue(missingValues.get(-1) > 0);
        }
    }

    @Test
    void missingPoolCssCanBeChangedAndDisabledWithoutAffectingNative() throws Exception {
        String css;
        try (var in = ChestRewardConfig.class.getResourceAsStream("/rewards.css")) {
            assertNotNull(in);
            css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var cfg = ChestRewardConfig.parse(css + """
            chest[kind="common"][pool="missing"] {
                mora: 0;
                sigils: 0;
                item-104011: 0;
            }
            """);
        var fallback = cfg.resolve(Kind.COMMON, 0, 0, 0, true).amounts();
        assertEquals(0, fallback.get(202));
        assertEquals(0, fallback.get(-1));
        assertEquals(0, fallback.get(104011));
        assertEquals(30, fallback.get(102));
        assertFalse(cfg.resolve(Kind.COMMON, 0, 0, 0).amounts().containsKey(202));
    }

    @Test
    void specificityBeatsLateBroadRulesAndFileOrderBreaksTies() {
        var cfg = ChestRewardConfig.parse(RULES + """
            chest[kind="common"][scene="3"][area1="1"][area2="104"] { primogems: 8; }
            chest[kind="common"][scene="3"][area1="1"] { primogems: 0; }
            chest[kind="common"] { primogems: 9; }
            chest[kind="common"][scene="3"][area1="1"][area2="104"] { primogems: 7; }
            """);
        assertEquals(Map.of(102, 30, 201, 7), values(cfg.resolve(Kind.COMMON, 3, 1, 104)));
        assertEquals(Map.of(102, 30), values(cfg.resolve(Kind.COMMON, 3, 1, 105)));
        assertEquals(Map.of(102, 30, 201, 9), values(cfg.resolve(Kind.COMMON, 3, 2, 0)));
    }

    @Test
    void commaSeparatedSelectorsAndZeroClearsWork() {
        var cfg = ChestRewardConfig.parse(RULES + """
            chest[kind="common"][scene="3"][area1="1"],
            chest[kind="common"][scene="3"][area1="2"] {
                primogems: 0;
                item-104001: 2;
            }
            chest[kind="common"][scene="3"][area1="2"][area2="201"] {
                item-104001: 0;
            }
            """);
        assertEquals(Map.of(102, 30, 104001, 2), values(cfg.resolve(Kind.COMMON, 3, 1, 0)));
        assertEquals(Map.of(102, 30), values(cfg.resolve(Kind.COMMON, 3, 2, 201)));
        assertEquals(Map.of(102, 30, 201, 2), values(cfg.resolve(Kind.COMMON, 3, 3, 0)));
    }

    @Test
    void invalidOrUnsupportedSelectorsFailClosed() {
        for (String selector : List.of(
                "chest[kind=\"remarkable\"]", "chest[kind=\"common\"][area1=\"1\"]",
                "chest[kind=\"common\"][area2=\"104\"]", "chest[kind=\"common\"][scene=\"0\"]",
                "chest[kind=\"common\"][scene=\"03\"]", "chest[kind=\"common\"][scene=\"2147483648\"]",
                "chest[kind=\"common\"][pool=\"unknown\"]",
                "chest[kind=\"common\"][point=\"6\"]", "chest[kind=\"common\"]:hover",
                "chest.kind", "chest > chest", "chest[id=\"9\"]",
                "chest[kind=\"common\"][kind=\"exquisite\"]")) {
            assertThrows(IllegalArgumentException.class,
                    () -> ChestRewardConfig.parse(RULES + selector + " { primogems: 3; }"), selector);
        }
    }

    @Test
    void invalidOrUnsupportedDeclarationsFailClosed() {
        for (String body : List.of(
                "primogems: -1;", "primogems: 1.5;", "primogems: 2147483648;",
                "primogems: auto;", "primogems: 2 !important;", "dropId: 123;",
                "items: [104001];", "item-0: 2;", "item-2147483648: 2;",
                "item-104001: 1; item-104001: 2;",
                "adventureExp: 1; item-102: 2;")) {
            assertThrows(IllegalArgumentException.class,
                    () -> ChestRewardConfig.parse(RULES + "chest[kind=\"common\"] { " + body + " }"),
                    body);
        }
        assertThrows(IllegalArgumentException.class,
                () -> ChestRewardConfig.parse(RULES + "@media screen { chest { primogems: 9; } }"));
        assertThrows(IllegalArgumentException.class,
                () -> ChestRewardConfig.parse(RULES + "@import url('remote.css');"));
    }

    @Test
    void missingDefaultsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ChestRewardConfig.parse("chest[kind=\"common\"] { primogems: 2; }"));
        assertThrows(IllegalArgumentException.class,
                () -> ChestRewardConfig.parse("transPoint { adventureExp: 50; }"));
    }
}
