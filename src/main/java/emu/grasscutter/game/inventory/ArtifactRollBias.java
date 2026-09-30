package emu.grasscutter.game.inventory;

import emu.grasscutter.data.GameDepot;
import emu.grasscutter.data.excels.reliquary.ReliquaryAffixData;
import emu.grasscutter.game.props.FightProperty;

/** Global artifact substat weighting rules. */
@FunctionalInterface
public interface ArtifactRollBias {
    ArtifactRollBias NONE = affix -> 1;
    ArtifactRollBias GLOBAL = ArtifactRollBias::tierWeight;

    /** Multiplier for the numeric roll tier of one affix row. */
    double weigh(ReliquaryAffixData affix);

    /** Multiplier for choosing a new substat type. */
    static double typeWeight(FightProperty prop) {
        return switch (prop) {
            case FIGHT_PROP_CRITICAL, FIGHT_PROP_CRITICAL_HURT -> 1.60;
            case FIGHT_PROP_CHARGE_EFFICIENCY, FIGHT_PROP_ELEMENT_MASTERY -> 1.20;
            case FIGHT_PROP_ATTACK_PERCENT,
                    FIGHT_PROP_HP_PERCENT,
                    FIGHT_PROP_DEFENSE_PERCENT -> 1.00;
            case FIGHT_PROP_ATTACK, FIGHT_PROP_HP, FIGHT_PROP_DEFENSE -> 0.60;
            default -> 1.00;
        };
    }

    /**
     * Maps the lowest through highest value tiers to 0.4 / 0.8 / 1.2 / 1.6.
     *
     * <p>Four-tier depots therefore normalize to 10% / 20% / 30% / 40% when their original tier
     * weights are equal. Depots with a different tier count are spread over the same range.
     */
    static double tierWeight(ReliquaryAffixData affix) {
        int tiers = GameDepot.getRelicAffixValueTierCount(affix);
        if (tiers <= 1) return 1.0;

        int tier = GameDepot.getRelicAffixValueTier(affix);
        double position = tier / (double) (tiers - 1);
        return 0.4 + 1.2 * position;
    }
}
