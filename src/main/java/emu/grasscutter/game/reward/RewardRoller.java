package emu.grasscutter.game.reward;

import emu.grasscutter.game.drop.YamlLootTables;
import emu.grasscutter.data.ItemIdRegistry;
import java.util.Map;
import java.util.TreeMap;
import java.util.random.RandomGenerator;

/** Executes CSS-selected loot tables, then applies per-item fixed overrides. */
public final class RewardRoller {
    private RewardRoller() {}

    public static Map<Integer, Integer> roll(
            CssRewards.Selection selection, YamlLootTables loot, RandomGenerator random) {
        return roll(selection, loot, random, RewardContext.NONE);
    }

    public static Map<Integer, Integer> roll(
            CssRewards.Selection selection, YamlLootTables loot, RandomGenerator random,
            RewardContext context) {
        var result = new TreeMap<Integer, Integer>();
        new TreeMap<>(selection.loot()).forEach((table, repetitions) -> {
            for (int i = 0; i < repetitions; i++) {
                loot.roll(table, random).forEach((id, count) ->
                        result.merge(id, count, Math::addExact));
            }
        });
        selection.items().forEach((id, count) -> {
            if (count == 0) result.remove(id);
            else result.put(id, count);
        });
        selection.rangedItems().forEach((id, spec) -> {
            int count = spec.sample(random);
            if (count == 0) result.remove(id);
            else result.put(id, count);
        });
        return resolveRegionalItems(result, context);
    }

    static Map<Integer, Integer> resolveRegionalItems(
            Map<Integer, Integer> items, RewardContext context) {
        if (!items.containsKey(ItemIdRegistry.REGIONAL_SIGIL)) {
            return Map.copyOf(items);
        }
        var resolved = new TreeMap<>(items);
        int count = resolved.remove(ItemIdRegistry.REGIONAL_SIGIL);
        if (count > 0) {
            try {
                int id = RegionalSigilResolver.resolve(context);
                resolved.merge(id, count, Math::addExact);
            } catch (IllegalArgumentException e) {
                emu.grasscutter.Grasscutter.getLogger().warn(
                        "Regional sigil omitted: count={} context={} reason={}",
                        count, context, e.getMessage());
            }
        }
        return Map.copyOf(resolved);
    }
}
