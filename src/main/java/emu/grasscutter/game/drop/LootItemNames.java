package emu.grasscutter.game.drop;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.utils.lang.Language;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * English resource names, stable aliases and IDs for operator-written loot.
 *
 * <p>English text is sourced from the loaded game's TextMap, not guessed from
 * item IDs. Wiki names guide the spelling of stable aliases. Ambiguous names
 * are rejected instead of silently resolving to a possibly wrong item.
 */
public final class LootItemNames implements YamlLootTables.ItemResolver {
    public record Info(int id, String english, String key, String category) {}
    private final Map<Integer, Info> byId;
    private final Map<String, Integer> byKey;
    private final Set<String> ambiguous;

    private static final Map<Integer, String> VERIFIED_ENGLISH = Map.of(
            104001, "Wanderer's Advice",
            104002, "Adventurer's Experience",
            104003, "Hero's Wit",
            202, "Mora",
            201, "Primogem");

    public LootItemNames(Map<Integer, String> englishNames) {
        this(englishNames, Map.of());
    }

    public LootItemNames(Map<Integer, String> englishNames, Map<Integer, String> categories) {
        var ids = new LinkedHashMap<Integer, Info>();
        var keys = new HashMap<String, Integer>();
        var duplicates = new java.util.HashSet<String>();
        for (var entry : englishNames.entrySet()) {
            int id = entry.getKey();
            String english = entry.getValue();
            if (id <= 0) throw new IllegalArgumentException("Invalid item ID: " + id);
            if (english == null || english.isBlank()) english = VERIFIED_ENGLISH.get(id);
            String key = toKey(english);
            String category = categories.getOrDefault(id, switch (id) {
                case 104001, 104002, 104003 -> "Character EXP Materials";
                case 201, 202 -> "Currencies";
                default -> "";
            });
            ids.put(id, new Info(id, english, key, category));
            add(keys, duplicates, key, id);
            add(keys, duplicates, english, id);
        }
        // Verified English item names from the Wiki, resolved to 7.1 resource IDs.
        for (var pair : VERIFIED_ENGLISH.entrySet()) {
            if (ids.containsKey(pair.getKey())) {
                add(keys, duplicates, pair.getValue(), pair.getKey());
                add(keys, duplicates, toKey(pair.getValue()), pair.getKey());
            }
        }
        byId = Map.copyOf(ids);
        byKey = Map.copyOf(keys);
        ambiguous = Set.copyOf(duplicates);
    }

    public static LootItemNames fromGameResources() {
        var names = new LinkedHashMap<Integer, String>();
        var categories = new LinkedHashMap<Integer, String>();
        for (var item : GameData.getItemDataMap().values()) {
            var text = Language.getTextMapKey(item.getNameTextMapHash());
            String english = text == null ? null : text.get(0);
            if (english != null && (english.isBlank() || english.startsWith("[N/A]"))) english = null;
            // Every real item must keep its numeric-ID fallback, even without English text.
            names.put(item.getId(), english);
            categories.put(item.getId(), categoryOf(item.getItemType(), item.getMaterialType()));
        }
        return new LootItemNames(names, categories);
    }

    /**
     * A resource-derived broad category, following the Wiki's English category vocabulary.
     * It is not a claim that every Wiki subcategory is represented in game Excel data.
     */
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

    @Override
    public int resolve(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Empty item name");
        String key = raw.trim();
        if (key.matches("[0-9]+")) {
            try {
                int id = Integer.parseInt(key);
                if (byId.containsKey(id)) return id;
            } catch (NumberFormatException ignored) {
                // A non-item ID must never become a raw virtual drop-table reference.
            }
            throw new IllegalArgumentException("Unknown item ID: " + key);
        }
        String index = normalise(key);
        if (ambiguous.contains(index)) throw new IllegalArgumentException("Ambiguous item name: " + key);
        Integer id = byKey.get(index);
        if (id == null) throw new IllegalArgumentException("Unknown item name: " + key);
        return id;
    }

    public Info describe(int id) { return byId.get(id); }
    public Map<Integer, Info> all() { return byId; }

    /** Hero's Wit -> Genshin.HerosWit; punctuation removed, words PascalCased. */
    static String toKey(String english) {
        if (english == null) return "";
        String[] words = english.replace("'", "").replace("’", "").split("[^A-Za-z0-9]+");
        var result = new StringBuilder("Genshin.");
        for (String word : words) {
            if (word.isEmpty()) continue;
            String lower = word.toLowerCase(Locale.ROOT);
            result.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
        }
        return result.length() == "Genshin.".length() ? "" : result.toString();
    }

    private static void add(Map<String, Integer> keys, Set<String> duplicates, String name, int id) {
        if (name == null || name.isBlank()) return;
        String key = normalise(name);
        Integer previous = keys.putIfAbsent(key, id);
        if (previous != null && previous != id) duplicates.add(key);
    }

    private static String normalise(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
