package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RewardCssSourceTest {
    @Test
    void translatesReadableInternalNamesIncludingNestedTemplateCalls() {
        var translated = RewardCssSource.translate("""
                boss {
                    genshin.avatar_ascension.anemo.sliver: 1;
                    astaps.loot.gem<3,<ELEMENT>[0],<ELEMENT>[1]>: 2;
                    element: "[anemo, cryo]";
                }
                """);
        assertTrue(translated.css().contains("--genshin-avatar_ascension-anemo-sliver: 1;"));
        assertEquals("astaps.loot.gem<3,<ELEMENT>[0],<ELEMENT>[1]>",
                translated.names().get("--astaps-loot-gem<3,<ELEMENT>[0],<ELEMENT>[1]>"));
        assertTrue(translated.css().contains("--astaps-loot-gem\\<3\\,\\<ELEMENT\\>\\[0\\]\\,\\<ELEMENT\\>\\[1\\]\\>: 2;"));
        assertEquals("element", translated.names().get("--element"));
    }

    @Test
    void preservesSelectorsCommentsQuotedValuesAndDeclarationsAfterNestedRules() {
        var translated = RewardCssSource.translate("""
                /* genshin.currency.mora: 999; { } */
                chest[data-note="genshin.currency.mora: 9; }"] {
                    genshin.currency.mora /* property comment */ : "1:50,2:50";
                    genshin.currency.mora:hover { genshin.currency.primogem: 3; }
                    &[data-note="a\\\";}"] { genshin.currency.mora: 5; }
                    genshin.currency.mora: 7;
                }
                """);
        assertTrue(translated.css().contains("/* genshin.currency.mora: 999; { } */"));
        assertTrue(translated.css().contains("[data-note=\"genshin.currency.mora: 9; }\"]"));
        assertTrue(translated.css().contains("--genshin-currency-mora /* property comment */ : \"1:50,2:50\";"));
        assertTrue(translated.css().contains("genshin.currency.mora:hover { --genshin-currency-primogem: 3; }"));
        assertTrue(translated.css().contains("--genshin-currency-mora: 7;"));
        assertEquals(Map.of("--genshin-currency-mora", "genshin.currency.mora",
                "--genshin-currency-primogem", "genshin.currency.primogem"), translated.names());
    }

    @Test
    void rejectsAmbiguousHyphenMappingAndFormerCustomPropertySyntax() {
        var error = assertThrows(IllegalArgumentException.class, () -> RewardCssSource.translate(
                "chest { astaps.loot.a.b: 1; astaps.loot.a-b: 2; }"));
        assertTrue(error.getMessage().contains("astaps.loot.a.b"));
        assertTrue(error.getMessage().contains("astaps.loot.a-b"));
        assertThrows(IllegalArgumentException.class, () -> RewardCssSource.translate(
                "chest { genshin.currency.mora: 1; --genshin-currency-mora: 2; }"));
        assertThrows(IllegalArgumentException.class, () -> RewardCssSource.translate(
                "chest { --grant-genshin\\.currency\\.mora: 1; }"));
    }

    @Test
    void mapsErrorColumnsAfterMultipleReplacementsBackToOriginalSource() {
        String original = "chest { genshin.currency.mora: 1; genshin.currency.primogem: @; }";
        var translated = RewardCssSource.translate(original);
        assertEquals("1:" + (original.indexOf('@') + 1),
                translated.location(1, translated.css().indexOf('@') + 1));
        assertEquals("genshin.currency.mora", translated.display("--genshin-currency-mora"));
        String multiline = "chest {\n  genshin.currency.mora: @;\n}";
        var second = RewardCssSource.translate(multiline);
        assertEquals("2:26", second.location(2, 28));
    }
}
