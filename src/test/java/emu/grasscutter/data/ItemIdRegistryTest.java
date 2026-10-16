package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemIdRegistryTest {
    @Test
    void resourceIdUsesThreeDottedParts() {
        var id = ResourceId.parse("genshin.currency.mora");
        assertEquals("genshin.currency.mora", id.toString());
        assertEquals("--genshin-currency-mora", id.toCssProperty());
        assertEquals(id, ResourceId.fromCssProperty("--genshin-currency-mora"));
        assertEquals(new ResourceId("my_mod", "item", "my-item"),
                ResourceId.fromCssProperty("--my_mod-item-my-item"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("genshin.item"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("Genshin.item.mora"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("genshin.currency.mora.more"));
        assertEquals("genshin.artifact.berserker",
                ResourceId.parse("genshin.artifact.berserker").toString());
        assertThrows(IllegalArgumentException.class, () ->
                ResourceId.parse("genshin.artifact.berserker.goblet.3.1"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("genshin:item:mora"));
        assertThrows(IllegalArgumentException.class,
                () -> ResourceId.fromCssProperty("--genshin--item--mora"));
    }

    @Test
    void itemIdsAreStrictlyTypedAndExistInLoadedResources() {
        var names = new ItemIdRegistry(Map.of(202, "Mora", 104003, "Hero's Wit"));
        assertEquals(202, names.resolve("genshin.currency.mora"));
        assertEquals(104003, names.resolve("genshin.avatar_exp.heros_wit"));
        assertThrows(IllegalArgumentException.class,
                () -> names.resolve("genshin.item.mora"));
        assertEquals(202, names.resolve("202"));
        assertThrows(IllegalArgumentException.class,
                () -> names.resolve("genshin.material.id_202"));
        assertThrows(IllegalArgumentException.class,
                () -> names.resolve("genshin.material.id_104003"));
        assertThrows(IllegalArgumentException.class,
                () -> names.resolve("genshin.material.id_999999"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("genshin.avatar.amber"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("astaps.item.mora"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("genshin.currency.primogem"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("genshin:item:mora"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("123456"));
    }

    @Test
    void nativeExperienceMaterialDomainsHaveDistinctShortIds() {
        var registry = new ItemIdRegistry(Map.of(
                104003, "Hero's Wit", 104011, "Enhancement Ore",
                104012, "Fine Enhancement Ore", 104013, "Mystic Enhancement Ore",
                105001, "Sanctifying Unction", 105002, "Sanctifying Essence",
                106, "Original Resin"), Map.of(
                104003, "Character EXP Materials",
                104011, "Weapon Enhancement Materials",
                104012, "Weapon Enhancement Materials",
                104013, "Weapon Enhancement Materials",
                105001, "Artifact EXP Materials",
                105002, "Artifact EXP Materials",
                106, "Currencies and Virtual Items"));
        assertEquals(104003, registry.resolve("genshin.avatar_exp.heros_wit"));
        assertEquals(104011, registry.resolve("genshin.weapon_exp.enhancement_ore"));
        assertEquals(104012, registry.resolve("genshin.weapon_exp.fine_ore"));
        assertEquals(104013, registry.resolve("genshin.weapon_exp.mystic_ore"));
        assertEquals(105001, registry.resolve("genshin.artifact_exp.unction"));
        assertEquals(105002, registry.resolve("genshin.artifact_exp.essence"));
        assertEquals(106, registry.resolve("genshin.currency.resin"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.material.heros_wit"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.weapon_exp.mystic_enhancement_ore"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.resource.resin"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("genshin.artifact_exp.mystic_ore"));
    }

    @Test
    void weaponsHaveOneCanonicalTypeWithoutItemAliases() {
        var names = new ItemIdRegistry(Map.of(11101, "Dull Blade", 202, "Mora"));
        assertEquals(11101, names.resolve("genshin.weapon.dull_blade"));
        assertEquals("genshin.weapon.dull_blade", names.describe(11101).key());
        assertThrows(IllegalArgumentException.class, () -> names.resolve("genshin.material.dull_blade"));
        assertThrows(IllegalArgumentException.class, () -> names.resolve("genshin.weapon.mora"));
        assertEquals(202, names.resolve("genshin.currency.mora"));
        var typed = new ItemIdRegistry(Map.of(7777, "Test Blade"),
                Map.of(7777, "Weapons"));
        assertEquals(7777, typed.resolve("genshin.weapon.test_blade"));
        assertThrows(IllegalArgumentException.class, () -> typed.resolve("genshin.material.test_blade"));
    }

    @Test
    void artifactReferenceNamesOnlyTheirSets() {
        assertTrue(ItemIdRegistry.isArtifactReference("genshin.artifact.berserker"));
        assertFalse(ItemIdRegistry.isArtifactReference("genshin.artifact.berserker.goblet.3.1"));
        assertTrue(ItemIdRegistry.chestDropNames().values().stream()
                .noneMatch(name -> name.startsWith("genshin.artifact.")));
    }

    @Test
    void identicalEnglishNamesDoNotSilentlySelectOneItem() {
        var names = new ItemIdRegistry(Map.of(501, "Duplicated", 502, "Duplicated"),
                Map.of(501, "Materials", 502, "Materials"));
        assertThrows(IllegalArgumentException.class,
                () -> names.resolve("genshin.material.duplicated"));
        assertEquals(501, names.resolve("501"));
        assertEquals(502, names.resolve("502"));
    }
}
