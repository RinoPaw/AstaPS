package emu.grasscutter.game.drop;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceId;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.ItemType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Resolves a set identity plus independent attributes to an original reliquary item ID.
 * Each roll selects slot, rarity, and then the initial append-prop count.
 */
public final class ArtifactResolver {
    private static final Map<Integer, String> SET_NAMES = Map.of(
            10005, "berserker",
            10007, "instructor",
            10009, "the_exile",
            10010, "adventurer",
            10011, "lucky_dog",
            10013, "traveling_doctor");

    public record Variant(int id, String set, int slot, int rarity, int substats) {}
    public record Selector(List<Variant> variants, LootCount.Spec slot,
                           LootCount.Spec rarity, LootCount.Spec substats) {
        public int select(RandomGenerator random) {
            int selectedSlot = slot == null
                    ? randomDistinct(variants, Variant::slot, random)
                    : slot.sample(random);
            List<Variant> matchingSlot = variants.stream()
                    .filter(v -> v.slot == selectedSlot).toList();
            if (matchingSlot.isEmpty()) {
                throw new IllegalArgumentException("Artifact slot not available: " + selectedSlot);
            }
            int selectedRarity = rarity == null
                    ? randomDistinct(matchingSlot, Variant::rarity, random)
                    : rarity.sample(random);
            List<Variant> matchingRarity = matchingSlot.stream()
                    .filter(v -> v.rarity == selectedRarity).toList();
            if (matchingRarity.isEmpty()) {
                throw new IllegalArgumentException("Artifact rarity not available: " + selectedRarity);
            }
            int selectedSubstats;
            if (substats != null) {
                selectedSubstats = substats.sample(random);
            } else {
                // Standard initial-substat ranges. Special or unusual native rows are
                // only selected when explicitly requested.
                int min = switch (selectedRarity) {
                    case 1, 2 -> 0;
                    case 3 -> 1;
                    case 4 -> 2;
                    case 5 -> 3;
                    default -> throw new IllegalArgumentException("Invalid artifact rarity");
                };
                int max = selectedRarity == 1 ? 0 : min + 1;
                List<Integer> valid = matchingRarity.stream().map(Variant::substats)
                        .distinct().filter(n -> n >= min && n <= max).sorted().toList();
                if (valid.isEmpty()) {
                    throw new IllegalArgumentException("No ordinary substat variant at rarity "
                            + selectedRarity);
                }
                selectedSubstats = valid.get(random.nextInt(valid.size()));
            }
            return matchingRarity.stream()
                    .filter(v -> v.substats == selectedSubstats)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No native artifact variant for slot " + selectedSlot
                            + ", rarity " + selectedRarity + ", substats " + selectedSubstats))
                    .id;
        }
    }

    private interface NumberOf {
        int get(Variant variant);
    }

    private static int randomDistinct(List<Variant> variants, NumberOf field,
                                      RandomGenerator random) {
        var choices = variants.stream().map(field::get).distinct().sorted().toList();
        return choices.get(random.nextInt(choices.size()));
    }

    private final Map<String, List<Variant>> bySet;

    public ArtifactResolver(List<Variant> variants) {
        var sets = new LinkedHashMap<String, List<Variant>>();
        for (Variant v : variants) {
            if (v.id <= 0 || v.slot < 1 || v.slot > 5 || v.rarity < 1 || v.rarity > 5
                    || v.substats < 0 || v.substats > 4) {
                throw new IllegalArgumentException("Invalid native artifact variant: " + v);
            }
            sets.computeIfAbsent(v.set, ignored -> new ArrayList<>()).add(v);
        }
        sets.replaceAll((name, list) -> List.copyOf(list.stream()
                .sorted(Comparator.comparingInt(Variant::id)).toList()));
        for (var entry : sets.entrySet()) {
            var seen = new java.util.HashSet<String>();
            for (Variant v : entry.getValue()) {
                String key = v.slot + ":" + v.rarity + ":" + v.substats;
                if (!seen.add(key)) {
                    throw new IllegalArgumentException("Ambiguous native artifact variant "
                            + entry.getKey() + " " + key);
                }
            }
        }
        this.bySet = Map.copyOf(sets);
    }

    public static ArtifactResolver fromGameResources() {
        var variants = new ArrayList<Variant>();
        for (ItemData item : GameData.getItemDataMap().values()) {
            if (item.getItemType() != ItemType.ITEM_RELIQUARY) continue;
            String set = SET_NAMES.get(item.getSetId());
            if (set == null) continue;
            int slot = item.getEquipType().getValue();
            // Special affix depots may duplicate ordinary set/slot/rarity/count combinations.
            if (item.getAppendPropDepotId() != item.getRankLevel() * 100 + 1) continue;
            if (slot < 1 || slot > 5) continue;
            variants.add(new Variant(item.getId(), set, slot,
                    item.getRankLevel(), item.getAppendPropNum()));
        }
        return new ArtifactResolver(variants);
    }

    public static String slotName(int slot) {
        return switch (slot) {
            case 1 -> "flower";
            case 2 -> "plume";
            case 3 -> "sands";
            case 4 -> "goblet";
            case 5 -> "circlet";
            default -> throw new IllegalArgumentException("Unknown artifact slot: " + slot);
        };
    }

    private static LootCount.Spec property(String field, Object raw) {
        if (raw == null) return null;
        if ("slot".equals(field) && raw instanceof String text) {
            raw = switch (text) {
                case "flower" -> 1;
                case "plume" -> 2;
                case "sands" -> 3;
                case "goblet" -> 4;
                case "circlet" -> 5;
                default -> raw;
            };
        }
        return LootCount.parse(raw);
    }

    public Selector compile(String name, Object rawProperties) {
        ResourceId id = ResourceId.parse(name);
        if (!"genshin".equals(id.namespace()) || !"artifact".equals(id.type())) {
            throw new IllegalArgumentException("Expected genshin.artifact.<set>: " + name);
        }
        List<Variant> variants = bySet.get(id.name());
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("Unknown native artifact set: " + name);
        }
        Map<?, ?> props = rawProperties == null ? Map.of() :
                rawProperties instanceof Map<?, ?> m ? m :
                throwInvalidProperties();
        for (Object key : props.keySet()) {
            if (!"slot".equals(key) && !"rarity".equals(key) && !"substats".equals(key)) {
                throw new IllegalArgumentException("Unknown artifact property: " + key);
            }
        }
        return new Selector(variants, property("slot", props.get("slot")),
                property("rarity", props.get("rarity")),
                property("substats", props.get("substats")));
    }

    private static Map<?, ?> throwInvalidProperties() {
        throw new IllegalArgumentException("Artifact properties must be a YAML mapping");
    }
}
