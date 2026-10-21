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
        var executions = new TreeMap<>(selection.loot());
        selection.rangedLoot().forEach((table, spec) ->
                executions.put(table, spec.sample(random)));
        executions.forEach((table, repetitions) -> {
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

    /** Execute every Lua/CSS/YAML choice as a fully qualified resource name. */
    public static Map<String, Integer> rollKeys(
            CssRewards.KeySelection selection, YamlLootTables loot, RandomGenerator random) {
        var result = new TreeMap<String, Integer>();
        var executions = new TreeMap<>(selection.loot());
        selection.rangedLoot().forEach((table, spec) ->
                executions.put(table, spec.sample(random)));
        executions.forEach((table, repetitions) -> {
            for (int i = 0; i < repetitions; i++) {
                loot.rollKeys(table, random).forEach((key, count) ->
                        result.merge(key, count, Math::addExact));
            }
        });
        selection.items().forEach((key, count) -> {
            if (count == 0) result.remove(key);
            else result.put(key, count);
        });
        selection.rangedItems().forEach((key, spec) -> {
            int count = spec.sample(random);
            if (count == 0) result.remove(key);
            else result.put(key, count);
        });
        return Map.copyOf(result);
    }

    /** One name lookup stage for all generated reward keys. */
    public static Map<Integer, Integer> resolveKeys(
            Map<String, Integer> keys, YamlLootTables loot, RewardContext context) {
        var result = new TreeMap<Integer, Integer>();
        keys.forEach((key, count) ->
                result.merge(loot.resolveKey(key), count, Math::addExact));
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
            final int id;
            try {
                id = RegionalSigilResolver.resolve(context);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "Cannot resolve regional sigil: count=" + count + " context=" + context, e);
            }
            resolved.merge(id, count, Math::addExact);
        }
        return Map.copyOf(resolved);
    }
}
