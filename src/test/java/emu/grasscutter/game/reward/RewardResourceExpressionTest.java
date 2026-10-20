package emu.grasscutter.game.reward;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RewardResourceExpressionTest {
    @Test
    void templateArgumentListsAndVariableReferencesHaveDifferentStructure() {
        var literal = RewardResourceExpression.parse("astaps.loot.gem<anemo>", true);
        assertFalse(literal.hasVariables());
        literal.requireDeclared(Set.of());
        assertEquals(List.of("astaps.loot.gem<anemo>"), literal.expand(Map.of("anemo", List.of("geo"))));
        var variable = RewardResourceExpression.parse("astaps.loot.gem<<element>>", true);
        assertTrue(variable.hasVariables());
        variable.requireDeclared(Set.of("element"));
        assertEquals(List.of("astaps.loot.gem<anemo>"), variable.expand(Map.of("element", List.of("anemo"))));
    }

    @Test
    void resourceSegmentsDistinguishLiteralNamesFromVariables() {
        var literal = RewardResourceExpression.parse("genshin.avatar_ascension.anemo.sliver", false);
        assertFalse(literal.hasVariables());
        var variable = RewardResourceExpression.parse("genshin.avatar_ascension.<anemo>.sliver", false);
        assertThrows(IllegalArgumentException.class, () -> variable.requireDeclared(Set.of("element")));
        assertThrows(IllegalArgumentException.class, () -> variable.expand(Map.of()));
        assertEquals(List.of("genshin.avatar_ascension.geo.sliver"), variable.expand(Map.of("anemo", List.of("geo"))));
    }

    @Test
    void preservesLiteralArgumentsAndExpandsIndexedVariables() {
        var expression = RewardResourceExpression.parse("astaps.loot.gem<3,<element>[0],<element>[1]>", true);
        assertEquals(List.of("astaps.loot.gem<3,anemo,cryo>"),
                expression.expand(Map.of("element", List.of("anemo", "cryo"))));
        assertThrows(IllegalArgumentException.class, () -> expression.expand(Map.of("element", List.of("anemo"))));
        var unindexed = RewardResourceExpression.parse("astaps.loot.gem<<element>>", true);
        assertThrows(IllegalArgumentException.class, () -> unindexed.expand(Map.of("element", List.of("anemo", "cryo"))));
    }

    @Test
    void itemListBindingsExpandToSeparateResources() {
        var expression = RewardResourceExpression.parse("genshin.avatar_ascension.<material>", false);
        assertEquals(List.of("genshin.avatar_ascension.hurricane_seed", "genshin.avatar_ascension.marionette_core"),
                expression.expand(Map.of("material", List.of("hurricane_seed", "marionette_core"))));
    }

    @Test
    void rejectsMalformedBracketsAndTrailingTemplateText() {
        for (String source : List.of("astaps.loot.gem<<element>", "astaps.loot.gem<anemo>>",
                "astaps.loot.gem<anemo>tail", "astaps.loot.gem<<element>[-1]>",
                "astaps.loot.gem<<element>[2147483648]>", "astaps.loot.gem<<a,b>>")) {
            assertThrows(IllegalArgumentException.class, () -> RewardResourceExpression.parse(source, true));
        }
    }
}
