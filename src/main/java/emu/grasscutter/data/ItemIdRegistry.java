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
            Map.entry(101, "character"),
            Map.entry(102, "adventure"),
            Map.entry(105, "companionship"),
            Map.entry(106, "resin"),
            Map.entry(107, "legendary_key"),
            Map.entry(121, "home"),
            Map.entry(201, "primogem"),
            Map.entry(202, "mora"),
            Map.entry(203, "genesis_crystal"),
            Map.entry(204, "realm_currency"),
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
            Map.entry(104012, "fine_ore"),
            Map.entry(104013, "mystic_ore"));

    // Explicit item identities for the historic chest reward graph.
    // Artifact identities are generated from native set/slot/rarity data at runtime.
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

    // Verified chest weapon IDs, also used when fixtures have no category metadata.
    private static final Set<Integer> CHEST_WEAPON_IDS = Set.of(
            11101, 11201, 11303, 11305, 12101, 12201, 12303, 12306,
            13101, 13201, 13301, 13302, 14101, 14201, 14303, 14305,
            15101, 15201, 15303, 15305);

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
            String category = categories.getOrDefault(id, switch (id) {
                case 104001, 104002, 104003 -> "Character EXP Materials";
                case 104011, 104012, 104013 -> "Weapon Enhancement Materials";
                case 201, 202 -> "Currencies";
                default -> CHEST_WEAPON_IDS.contains(id) ? "Weapons" : "";
            });
            String name = "Artifacts".equals(category) ? "" :
                    STABLE_NAMES.containsKey(id) ? STABLE_NAMES.get(id)
                    : CHEST_DROP_NAMES.containsKey(id) ? CHEST_DROP_NAMES.get(id)
                    : shortName(category, english);
            String type = resourceType(id, category);
            String key = name.isEmpty() || type == null ? null :
                    new ResourceId("genshin", type, name).toString();
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
        // a bag material. Its explicit native virtual ID can be absent from Excel.
        englishNames.putIfAbsent(102, "Adventure EXP");
        categories.putIfAbsent(102, "Currencies and Virtual Items");
        return new ItemIdRegistry(englishNames, categories);
    }

    /** Short forms are scoped to their exact native material categories. */
    static String shortName(String category, String english) {
        String name = slug(english);
        if ("Artifact EXP Materials".equals(category)) {
            if ("sanctifying_unction".equals(name)) return "unction";
            if ("sanctifying_essence".equals(name)) return "essence";
        }
        return name;
    }

    /**
     * Map native resource identity, not a vague catch-all "item" category.
     * Virtual rewards are explicit because these IDs affect different player properties.
     * A resource with unknown native classification has no guessed semantic ID.
     */
    static String resourceType(int id, String category) {
        return switch (id) {
            case 101, 102, 105, 121 -> "experience";
            case 106, 107, 201, 202, 203, 204, 301, 302, 303, 304, 305, 306,
                    307, 308, 101692 -> "currency";
            default -> switch (category) {
                case "Weapons" -> "weapon";
                case "Food" -> "food";
                case "Furnishings" -> "furnishing";
                case "Character EXP Materials" -> "avatar_exp";
                case "Weapon Enhancement Materials" -> "weapon_exp";
                case "Artifact EXP Materials" -> "artifact_exp";
                case "Artifacts", "Other Items", "Currencies and Virtual Items", "" ->
                        CHEST_DROP_NAMES.containsKey(id) || STABLE_NAMES.containsKey(id)
                                ? "material" : null;
                case "Materials", "Character Development Items",
                        "Talent Level-Up Materials", "Quest Items", "Furnishing Blueprints",
                        "Wood", "Bait", "Fishing Rods", "Wind Gliders", "Namecards",
                        "Outfits", "Seeds" -> "material";
                default -> throw new IllegalArgumentException(
                        "Unknown native resource category for " + id + ": " + category);
            };
        };
    }

    /** Artifacts use the same dotted resource convention as other game items. */
    public static boolean isArtifactReference(String reference) {
        return reference != null && reference.matches(
                "genshin\\.artifact\\.[a-z][a-z0-9_]*");
    }

    public int resolve(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Missing item reference");
        }
        String input = reference.trim();
        if (input.equalsIgnoreCase("genshin.currency.sigil")) {
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
                    || !Set.of("currency", "experience", "avatar_exp", "weapon_exp",
                            "artifact_exp", "material", "food", "furnishing", "weapon")
                            .contains(resource.type())) {
                throw new IllegalArgumentException("Unsupported inventory resource: " + input);
            }
        }

        String name = input.toLowerCase(Locale.ROOT);
        if (ambiguous.contains(name)) {
            throw new IllegalArgumentException("Ambiguous item name: " + input);
        }
        Integer id = byName.get(name);
        if (id == null) throw new IllegalArgumentException("Unknown item: " + input);
        if (input.contains(".") && !input.equalsIgnoreCase(byId.get(id).key())) {
            throw new IllegalArgumentException("Wrong resource type for item: " + input
                    + " (expected " + byId.get(id).key() + ")");
        }
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
