package emu.grasscutter.game.reward;

import emu.grasscutter.game.drop.YamlLootTables;
import java.util.Map;
import java.util.TreeMap;
import java.util.random.RandomGenerator;

/** Executes CSS-selected loot tables, then applies per-item fixed overrides. */
public final class RewardRoller {
    private RewardRoller() {}

    public static Map<Integer, Integer> roll(
            CssRewards.Selection selection, YamlLootTables loot, RandomGenerator random) {
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
        return Map.copyOf(result);
    }
}
