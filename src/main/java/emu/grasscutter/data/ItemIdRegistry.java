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
            String name = STABLE_NAMES.getOrDefault(id, slug(english));
            String key = name.isEmpty() ? null : new ResourceId("genshin", "item", name).toString();
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
            if (!resource.namespace().equals("genshin") || !resource.type().equals("item")) {
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
