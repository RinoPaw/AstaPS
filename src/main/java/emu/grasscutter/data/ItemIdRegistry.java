package emu.grasscutter.data;

import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.utils.lang.Language;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Item identities shared by loot.yaml and rewards.css.
 *
 * <p>Known stable names are explicit. Other names are provisional 7.1 English
 * TextMap slugs: a collision is an error, never a silent choice of item ID.
 * An unnamed item remains addressable by its numeric ID.
 */
public final class ItemIdRegistry {
    /** Internal placeholder, never a real game item ID. */
    public static final int REGIONAL_SIGIL = -1;

    public record Info(int id, String english, String key, String category) {}

    private static final Map<Integer, String> VERIFIED_ENGLISH = Map.of(
            104001, "Wanderer's Advice",
            104002, "Adventurer's Experience",
            104003, "Hero's Wit",
            201, "Primogem",
            202, "Mora");

    private static final Map<Integer, String> STABLE_NAMES = Map.ofEntries(
            Map.entry(102, "adventure_exp"),
            Map.entry(201, "primogem"),
            Map.entry(202, "mora"),
            Map.entry(301, "pyro_sigil"),
            Map.entry(302, "hydro_sigil"),
            Map.entry(303, "dendro_sigil"),
            Map.entry(304, "electro_sigil"),
            Map.entry(305, "anemo_sigil"),
            Map.entry(306, "cryo_sigil"),
            Map.entry(307, "geo_sigil"),
            Map.entry(308, "lunar_sigil"),
            Map.entry(104001, "wanderers_advice"),
            Map.entry(104002, "adventurers_experience"),
            Map.entry(104003, "heros_wit"),
            Map.entry(104011, "enhancement_ore"),
            Map.entry(104012, "fine_enhancement_ore"),
            Map.entry(104013, "mystic_enhancement_ore"));

    // Explicit item identities for the historic chest reward graph.
    // Native artifact IDs encode set, slot, rarity and initial substat count.
    // Semantic artifact references use artifact:set:slot:rarity:initial_count.
    private static final Map<Integer, String> CHEST_DROP_NAMES = Map.ofEntries(
            Map.entry(11101, "dull_blade"),
            Map.entry(11201, "silver_sword"),
            Map.entry(11303, "travelers_handy_sword"),
            Map.entry(11305, "fillet_blade"),
            Map.entry(12101, "waster_greatsword"),
            Map.entry(12201, "old_mercs_pal"),
            Map.entry(12303, "ferrous_shadow"),
            Map.entry(12306, "skyrider_greatsword"),
            Map.entry(13101, "beginners_protector"),
            Map.entry(13201, "iron_point"),
            Map.entry(13301, "white_tassel"),
            Map.entry(13302, "halberd"),
            Map.entry(14101, "apprentices_notes"),
            Map.entry(14201, "pocket_grimoire"),
            Map.entry(14303, "otherworldly_story"),
            Map.entry(14305, "twin_nephrite"),
            Map.entry(15101, "hunters_bow"),
            Map.entry(15201, "seasoned_hunters_bow"),
            Map.entry(15303, "recurve_bow"),
            Map.entry(15305, "messenger"),
            Map.entry(55311, "genshin.artifact.berserker.goblet.3.1"),
            Map.entry(55312, "genshin.artifact.berserker.goblet.3.2"),
            Map.entry(55321, "genshin.artifact.berserker.plume.3.1"),
            Map.entry(55322, "genshin.artifact.berserker.plume.3.2"),
            Map.entry(55331, "genshin.artifact.berserker.circlet.3.1"),
            Map.entry(55332, "genshin.artifact.berserker.circlet.3.2"),
            Map.entry(55341, "genshin.artifact.berserker.flower.3.1"),
            Map.entry(55342, "genshin.artifact.berserker.flower.3.2"),
            Map.entry(55351, "genshin.artifact.berserker.sands.3.1"),
            Map.entry(55352, "genshin.artifact.berserker.sands.3.2"),
            Map.entry(55412, "genshin.artifact.berserker.goblet.4.2"),
            Map.entry(55413, "genshin.artifact.berserker.goblet.4.3"),
            Map.entry(55422, "genshin.artifact.berserker.plume.4.2"),
            Map.entry(55423, "genshin.artifact.berserker.plume.4.3"),
            Map.entry(55432, "genshin.artifact.berserker.circlet.4.2"),
            Map.entry(55433, "genshin.artifact.berserker.circlet.4.3"),
            Map.entry(55442, "genshin.artifact.berserker.flower.4.2"),
            Map.entry(55443, "genshin.artifact.berserker.flower.4.3"),
            Map.entry(55452, "genshin.artifact.berserker.sands.4.2"),
            Map.entry(55453, "genshin.artifact.berserker.sands.4.3"),
            Map.entry(57311, "genshin.artifact.instructor.goblet.3.1"),
            Map.entry(57312, "genshin.artifact.instructor.goblet.3.2"),
            Map.entry(57321, "genshin.artifact.instructor.plume.3.1"),
            Map.entry(57322, "genshin.artifact.instructor.plume.3.2"),
            Map.entry(57331, "genshin.artifact.instructor.circlet.3.1"),
            Map.entry(57332, "genshin.artifact.instructor.circlet.3.2"),
            Map.entry(57341, "genshin.artifact.instructor.flower.3.1"),
            Map.entry(57342, "genshin.artifact.instructor.flower.3.2"),
            Map.entry(57351, "genshin.artifact.instructor.sands.3.1"),
            Map.entry(57352, "genshin.artifact.instructor.sands.3.2"),
            Map.entry(57412, "genshin.artifact.instructor.goblet.4.2"),
            Map.entry(57413, "genshin.artifact.instructor.goblet.4.3"),
            Map.entry(57422, "genshin.artifact.instructor.plume.4.2"),
            Map.entry(57423, "genshin.artifact.instructor.plume.4.3"),
            Map.entry(57432, "genshin.artifact.instructor.circlet.4.2"),
            Map.entry(57433, "genshin.artifact.instructor.circlet.4.3"),
            Map.entry(57442, "genshin.artifact.instructor.flower.4.2"),
            Map.entry(57443, "genshin.artifact.instructor.flower.4.3"),
            Map.entry(57452, "genshin.artifact.instructor.sands.4.2"),
            Map.entry(57453, "genshin.artifact.instructor.sands.4.3"),
            Map.entry(59311, "genshin.artifact.the_exile.goblet.3.1"),
            Map.entry(59312, "genshin.artifact.the_exile.goblet.3.2"),
            Map.entry(59321, "genshin.artifact.the_exile.plume.3.1"),
            Map.entry(59322, "genshin.artifact.the_exile.plume.3.2"),
            Map.entry(59331, "genshin.artifact.the_exile.circlet.3.1"),
            Map.entry(59332, "genshin.artifact.the_exile.circlet.3.2"),
            Map.entry(59341, "genshin.artifact.the_exile.flower.3.1"),
            Map.entry(59342, "genshin.artifact.the_exile.flower.3.2"),
            Map.entry(59351, "genshin.artifact.the_exile.sands.3.1"),
            Map.entry(59352, "genshin.artifact.the_exile.sands.3.2"),
            Map.entry(59412, "genshin.artifact.the_exile.goblet.4.2"),
            Map.entry(59413, "genshin.artifact.the_exile.goblet.4.3"),
            Map.entry(59422, "genshin.artifact.the_exile.plume.4.2"),
            Map.entry(59423, "genshin.artifact.the_exile.plume.4.3"),
            Map.entry(59432, "genshin.artifact.the_exile.circlet.4.2"),
            Map.entry(59433, "genshin.artifact.the_exile.circlet.4.3"),
            Map.entry(59442, "genshin.artifact.the_exile.flower.4.2"),
            Map.entry(59443, "genshin.artifact.the_exile.flower.4.3"),
            Map.entry(59452, "genshin.artifact.the_exile.sands.4.2"),
            Map.entry(59453, "genshin.artifact.the_exile.sands.4.3"),
            Map.entry(60110, "genshin.artifact.adventurer.goblet.1.0"),
            Map.entry(60120, "genshin.artifact.adventurer.plume.1.0"),
            Map.entry(60130, "genshin.artifact.adventurer.circlet.1.0"),
            Map.entry(60140, "genshin.artifact.adventurer.flower.1.0"),
            Map.entry(60150, "genshin.artifact.adventurer.sands.1.0"),
            Map.entry(60210, "genshin.artifact.adventurer.goblet.2.0"),
            Map.entry(60211, "genshin.artifact.adventurer.goblet.2.1"),
            Map.entry(60220, "genshin.artifact.adventurer.plume.2.0"),
            Map.entry(60221, "genshin.artifact.adventurer.plume.2.1"),
            Map.entry(60230, "genshin.artifact.adventurer.circlet.2.0"),
            Map.entry(60231, "genshin.artifact.adventurer.circlet.2.1"),
            Map.entry(60240, "genshin.artifact.adventurer.flower.2.0"),
            Map.entry(60241, "genshin.artifact.adventurer.flower.2.1"),
            Map.entry(60250, "genshin.artifact.adventurer.sands.2.0"),
            Map.entry(60251, "genshin.artifact.adventurer.sands.2.1"),
            Map.entry(60311, "genshin.artifact.adventurer.goblet.3.1"),
            Map.entry(60312, "genshin.artifact.adventurer.goblet.3.2"),
            Map.entry(60321, "genshin.artifact.adventurer.plume.3.1"),
            Map.entry(60322, "genshin.artifact.adventurer.plume.3.2"),
            Map.entry(60331, "genshin.artifact.adventurer.circlet.3.1"),
            Map.entry(60332, "genshin.artifact.adventurer.circlet.3.2"),
            Map.entry(60341, "genshin.artifact.adventurer.flower.3.1"),
            Map.entry(60342, "genshin.artifact.adventurer.flower.3.2"),
            Map.entry(60351, "genshin.artifact.adventurer.sands.3.1"),
            Map.entry(60352, "genshin.artifact.adventurer.sands.3.2"),
            Map.entry(61110, "genshin.artifact.lucky_dog.goblet.1.0"),
            Map.entry(61120, "genshin.artifact.lucky_dog.plume.1.0"),
            Map.entry(61130, "genshin.artifact.lucky_dog.circlet.1.0"),
            Map.entry(61140, "genshin.artifact.lucky_dog.flower.1.0"),
            Map.entry(61150, "genshin.artifact.lucky_dog.sands.1.0"),
            Map.entry(61210, "genshin.artifact.lucky_dog.goblet.2.0"),
            Map.entry(61211, "genshin.artifact.lucky_dog.goblet.2.1"),
            Map.entry(61220, "genshin.artifact.lucky_dog.plume.2.0"),
            Map.entry(61221, "genshin.artifact.lucky_dog.plume.2.1"),
            Map.entry(61230, "genshin.artifact.lucky_dog.circlet.2.0"),
            Map.entry(61231, "genshin.artifact.lucky_dog.circlet.2.1"),
            Map.entry(61240, "genshin.artifact.lucky_dog.flower.2.0"),
            Map.entry(61241, "genshin.artifact.lucky_dog.flower.2.1"),
            Map.entry(61250, "genshin.artifact.lucky_dog.sands.2.0"),
            Map.entry(61251, "genshin.artifact.lucky_dog.sands.2.1"),
            Map.entry(61311, "genshin.artifact.lucky_dog.goblet.3.1"),
            Map.entry(61312, "genshin.artifact.lucky_dog.goblet.3.2"),
            Map.entry(61321, "genshin.artifact.lucky_dog.plume.3.1"),
            Map.entry(61322, "genshin.artifact.lucky_dog.plume.3.2"),
            Map.entry(61331, "genshin.artifact.lucky_dog.circlet.3.1"),
            Map.entry(61332, "genshin.artifact.lucky_dog.circlet.3.2"),
            Map.entry(61341, "genshin.artifact.lucky_dog.flower.3.1"),
            Map.entry(61342, "genshin.artifact.lucky_dog.flower.3.2"),
            Map.entry(61351, "genshin.artifact.lucky_dog.sands.3.1"),
            Map.entry(61352, "genshin.artifact.lucky_dog.sands.3.2"),
            Map.entry(63110, "genshin.artifact.traveling_doctor.goblet.1.0"),
            Map.entry(63120, "genshin.artifact.traveling_doctor.plume.1.0"),
            Map.entry(63130, "genshin.artifact.traveling_doctor.circlet.1.0"),
            Map.entry(63140, "genshin.artifact.traveling_doctor.flower.1.0"),
            Map.entry(63150, "genshin.artifact.traveling_doctor.sands.1.0"),
            Map.entry(63210, "genshin.artifact.traveling_doctor.goblet.2.0"),
            Map.entry(63211, "genshin.artifact.traveling_doctor.goblet.2.1"),
            Map.entry(63220, "genshin.artifact.traveling_doctor.plume.2.0"),
            Map.entry(63221, "genshin.artifact.traveling_doctor.plume.2.1"),
            Map.entry(63230, "genshin.artifact.traveling_doctor.circlet.2.0"),
            Map.entry(63231, "genshin.artifact.traveling_doctor.circlet.2.1"),
            Map.entry(63240, "genshin.artifact.traveling_doctor.flower.2.0"),
            Map.entry(63241, "genshin.artifact.traveling_doctor.flower.2.1"),
            Map.entry(63250, "genshin.artifact.traveling_doctor.sands.2.0"),
            Map.entry(63251, "genshin.artifact.traveling_doctor.sands.2.1"),
            Map.entry(63311, "genshin.artifact.traveling_doctor.goblet.3.1"),
            Map.entry(63312, "genshin.artifact.traveling_doctor.goblet.3.2"),
            Map.entry(63321, "genshin.artifact.traveling_doctor.plume.3.1"),
            Map.entry(63322, "genshin.artifact.traveling_doctor.plume.3.2"),
            Map.entry(63331, "genshin.artifact.traveling_doctor.circlet.3.1"),
            Map.entry(63332, "genshin.artifact.traveling_doctor.circlet.3.2"),
            Map.entry(63341, "genshin.artifact.traveling_doctor.flower.3.1"),
            Map.entry(63342, "genshin.artifact.traveling_doctor.flower.3.2"),
            Map.entry(63351, "genshin.artifact.traveling_doctor.sands.3.1"),
            Map.entry(63352, "genshin.artifact.traveling_doctor.sands.3.2"),
            Map.entry(100061, "raw_meat"),
            Map.entry(100064, "fowl"),
            Map.entry(101692, "light_realm_sigil"),
            Map.entry(104301, "teachings_of_freedom"),
            Map.entry(104302, "guide_to_freedom"),
            Map.entry(104304, "teachings_of_resistance"),
            Map.entry(104305, "guide_to_resistance"),
            Map.entry(104307, "teachings_of_ballad"),
            Map.entry(104308, "guide_to_ballad"),
            Map.entry(104310, "teachings_of_prosperity"),
            Map.entry(104311, "guide_to_prosperity"),
            Map.entry(104313, "teachings_of_diligence"),
            Map.entry(104314, "guide_to_diligence"),
            Map.entry(104316, "teachings_of_gold"),
            Map.entry(104317, "guide_to_gold"),
            Map.entry(104320, "guide_to_transience"),
            Map.entry(104321, "philosophies_of_transience"),
            Map.entry(104323, "guide_to_elegance"),
            Map.entry(104324, "philosophies_of_elegance"),
            Map.entry(104326, "guide_to_light"),
            Map.entry(104327, "philosophies_of_light"),
            Map.entry(104329, "guide_to_admonition"),
            Map.entry(104330, "philosophies_of_admonition"),
            Map.entry(104332, "guide_to_ingenuity"),
            Map.entry(104333, "philosophies_of_ingenuity"),
            Map.entry(104335, "guide_to_praxis"),
            Map.entry(104336, "philosophies_of_praxis"),
            Map.entry(107010, "crimson_agate"),
            Map.entry(109202, "recipe_northern_smoked_chicken"),
            Map.entry(109207, "recipe_mushroom_pizza"),
            Map.entry(109406, "recipe_mora_meat"),
            Map.entry(109407, "recipe_stir_fried_filet"));

    private final Map<Integer, Info> byId;
    private final Map<String, Integer> byName;
    private final Set<String> ambiguous;

    public ItemIdRegistry(Map<Integer, String> englishNames) {
        this(englishNames, Map.of());
    }

    public ItemIdRegistry(Map<Integer, String> englishNames, Map<Integer, String> categories) {
        var ids = new LinkedHashMap<Integer, Info>();
        var names = new HashMap<String, Integer>();
        var collisions = new HashSet<String>();
        for (var entry : englishNames.entrySet()) {
            int id = entry.getKey();
            if (id <= 0) throw new IllegalArgumentException("Invalid item ID: " + id);
            String english = entry.getValue();
            if (english == null || english.isBlank()) english = VERIFIED_ENGLISH.get(id);
            String name = STABLE_NAMES.containsKey(id) ? STABLE_NAMES.get(id)
                    : CHEST_DROP_NAMES.getOrDefault(id, slug(english));
            String key = name.isEmpty() ? null : isArtifactReference(name)
                    ? name : new ResourceId("genshin", "item", name).toString();
            String category = categories.getOrDefault(id, switch (id) {
                case 104001, 104002, 104003 -> "Character EXP Materials";
                case 201, 202 -> "Currencies";
                default -> "";
            });
            ids.put(id, new Info(id, english, key, category));
            if (key != null) putUnique(names, collisions, key, id);
            if (english != null && !english.isBlank()) {
                putUnique(names, collisions, english, id);
            }
        }
        this.byId = Map.copyOf(ids);
        this.byName = Map.copyOf(names);
        this.ambiguous = Set.copyOf(collisions);
    }

    public static ItemIdRegistry fromGameResources() {
        var englishNames = new LinkedHashMap<Integer, String>();
        var categories = new LinkedHashMap<Integer, String>();
        for (var item : GameData.getItemDataMap().values()) {
            var strings = Language.getTextMapKey(item.getNameTextMapHash());
            String english = strings == null ? null : strings.get(0);
            if (english != null && (english.isBlank() || english.startsWith("["))) {
                english = null;
            }
            englishNames.put(item.getId(), english);
            categories.put(item.getId(), categoryOf(item.getItemType(), item.getMaterialType()));
        }
        // Adventure EXP (102) is settled by Player.earnExp, not dropped as
        // a bag item. It can be absent from material/weapon Excel resources.
        englishNames.putIfAbsent(102, "Adventure EXP");
        categories.putIfAbsent(102, "Currencies and Virtual Items");
        return new ItemIdRegistry(englishNames, categories);
    }

    /** Artifacts use the same dotted resource convention as other game items. */
    public static boolean isArtifactReference(String reference) {
        return reference != null && reference.matches(
                "genshin\\.artifact\\.[a-z][a-z0-9_]*\\.(flower|plume|sands|goblet|circlet)\\.[1-5]\\.[0-4]");
    }

    public int resolve(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Missing item reference");
        }
        String input = reference.trim();
        if (input.equalsIgnoreCase("sigil")
                || input.equalsIgnoreCase("genshin.item.sigil")) {
            return REGIONAL_SIGIL;
        }
        if (input.matches("[0-9]+")) {
            try {
                int id = Integer.parseInt(input);
                if (byId.containsKey(id)) return id;
            } catch (NumberFormatException ignored) {
                // Invalid numeric item IDs are rejected below.
            }
            throw new IllegalArgumentException("Unknown item ID: " + input);
        }

        if (input.matches("[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*\\..+")) {
            ResourceId resource = ResourceId.parse(input);
            if (!resource.namespace().equals("genshin")
                    || !(resource.type().equals("item") || resource.type().equals("artifact"))) {
                throw new IllegalArgumentException("Unsupported item resource: " + input);
            }
        }

        String name = input.toLowerCase(Locale.ROOT);
        if (ambiguous.contains(name)) {
            throw new IllegalArgumentException("Ambiguous item name: " + input);
        }
        Integer id = byName.get(name);
        if (id == null) throw new IllegalArgumentException("Unknown item: " + input);
        return id;
    }

    /** Stable item identities for the archived historical chest rewards. */
    public static Map<Integer, String> chestDropNames() {
        return CHEST_DROP_NAMES;
    }

    public Info describe(int id) {
        return byId.get(id);
    }

    public Map<Integer, Info> all() {
        return byId;
    }

    private static void putUnique(Map<String, Integer> names, Set<String> collisions,
            String reference, int id) {
        String key = reference.toLowerCase(Locale.ROOT);
        Integer old = names.putIfAbsent(key, id);
        if (old != null && old != id) collisions.add(key);
    }

    /** Provisional English TextMap spelling, not a verified cross-version ID. */
    static String slug(String english) {
        if (english == null) return "";
        String ascii = english.replace("'", "").replace("’", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (ascii.isEmpty() || !ascii.matches("[a-z][a-z0-9_]*")) return "";
        return ascii;
    }

    public static String categoryOf(ItemType type, MaterialType material) {
        if (type == null) return "";
        switch (type) {
            case ITEM_WEAPON, ITEM_TPS_WEAPON -> { return "Weapons"; }
            case ITEM_RELIQUARY -> { return "Artifacts"; }
            case ITEM_FURNITURE -> { return "Furnishings"; }
            case ITEM_VIRTUAL -> { return "Currencies and Virtual Items"; }
            case ITEM_MATERIAL -> {}
            default -> { return "Other Items"; }
        }
        if (material == null) return "Materials";
        return switch (material) {
            case MATERIAL_EXP_FRUIT -> "Character EXP Materials";
            case MATERIAL_WEAPON_EXP_STONE -> "Weapon Enhancement Materials";
            case MATERIAL_RELIQUARY_MATERIAL -> "Artifact EXP Materials";
            case MATERIAL_AVATAR_MATERIAL -> "Character Development Items";
            case MATERIAL_TALENT -> "Talent Level-Up Materials";
            case MATERIAL_FOOD, MATERIAL_SPICE_FOOD -> "Food";
            case MATERIAL_QUEST -> "Quest Items";
            case MATERIAL_FURNITURE_FORMULA, MATERIAL_FURNITURE_SUITE_FORMULA -> "Furnishing Blueprints";
            case MATERIAL_WOOD -> "Wood";
            case MATERIAL_FISH_BAIT -> "Bait";
            case MATERIAL_FISH_ROD -> "Fishing Rods";
            case MATERIAL_FLYCLOAK -> "Wind Gliders";
            case MATERIAL_NAMECARD -> "Namecards";
            case MATERIAL_COSTUME -> "Outfits";
            case MATERIAL_HOME_SEED -> "Seeds";
            default -> "Materials";
        };
    }
}
