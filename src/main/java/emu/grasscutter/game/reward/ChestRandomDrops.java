package emu.grasscutter.game.reward;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/** Prototype of independent Bernoulli item drops with an inclusive count range. */
public final class ChestRandomDrops {
    private ChestRandomDrops() {}

    /** Chance is in basis points: 0 = never, 10000 = always. */
    public record Roll(int itemId, int min, int max, int chance) {
        public Roll {
            if (itemId <= 0 || itemId == 102 || min <= 0 || max < min
                    || max >= Integer.MAX_VALUE || chance < 0 || chance > 10000) {
                throw new IllegalArgumentException("Invalid chest random item or range");
            }
        }
    }

    /** Sample once per chest; a miss contributes no item. */
    public static Map<Integer, Integer> sample(List<Roll> rolls, Random random) {
        Objects.requireNonNull(rolls, "rolls");
        Objects.requireNonNull(random, "random");
        var result = new LinkedHashMap<Integer, Integer>();
        for (Roll roll : rolls) {
            if (random.nextInt(10000) < roll.chance()) {
                int count = roll.min() + random.nextInt(roll.max() - roll.min() + 1);
                result.merge(roll.itemId(), count, Math::addExact);
            }
        }
        return Map.copyOf(result);
    }
}
