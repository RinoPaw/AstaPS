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

    @Test
    void dollarBindingsExpandResourcePathsAndIndexedLists() {
        var material = RewardResourceExpression.parse(
                "genshin.avatar_ascension.$material", false);
        material.requireDeclared(Set.of("material"));
        assertEquals(List.of("genshin.avatar_ascension.hurricane_seed",
                "genshin.avatar_ascension.marionette_core"),
                material.expand(Map.of("material", List.of("hurricane_seed", "marionette_core"))));
        var indexed = RewardResourceExpression.parse(
                "genshin.avatar_ascension.$element[1].sliver", false);
        assertEquals(List.of("genshin.avatar_ascension.cryo.sliver"),
                indexed.expand(Map.of("element", List.of("anemo", "cryo"))));
    }

    @Test
    void functionCallsDistinguishLiteralScalarAndSpreadArguments() {
        var literal = RewardResourceExpression.parse("astaps.loot.gem(anemo)", true);
        assertFalse(literal.hasVariables());
        assertEquals(List.of("astaps.loot.gem<anemo>"), literal.expand(Map.of()));
        var bound = RewardResourceExpression.parse(
                "astaps.loot.world_gem_two(3, ...$element)", true);
        assertEquals(List.of("astaps.loot.world_gem_two<3,anemo,cryo>"),
                bound.expand(Map.of("element", List.of("anemo", "cryo"))));
        var indexed = RewardResourceExpression.parse(
                "astaps.loot.world_gem_two(3, $element[0], $element[1])", true);
        assertEquals(bound.expand(Map.of("element", List.of("anemo", "cryo"))),
                indexed.expand(Map.of("element", List.of("anemo", "cryo"))));
    }

    @Test
    void functionCallArgumentPacksMustBeExplicit() {
        var unindexed = RewardResourceExpression.parse("astaps.loot.gem($element)", true);
        assertThrows(IllegalArgumentException.class,
                () -> unindexed.expand(Map.of("element", List.of("anemo", "cryo"))));
        assertThrows(IllegalArgumentException.class,
                () -> RewardResourceExpression.parse("astaps.loot.gem(...$element[0])", true));
        assertThrows(IllegalArgumentException.class,
                () -> RewardResourceExpression.parse("astaps.loot.gem(3,)", true));
        assertThrows(IllegalArgumentException.class,
                () -> RewardResourceExpression.parse("astaps.loot.gem($element[-1])", true));
        assertThrows(IllegalArgumentException.class,
                () -> RewardResourceExpression.parse("genshin.currency.$missing", false)
                    .requireDeclared(Set.of("element")));
    }
}
