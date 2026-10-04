package emu.grasscutter.game.inventory;

import static emu.grasscutter.config.Configuration.GAME;

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

    /** Multiplier for choosing a new or upgraded substat type. */
    static double typeWeight(FightProperty prop) {
        var weights = GAME.artifacts.rolls;
        return switch (prop) {
            case FIGHT_PROP_CRITICAL -> weights.critical;
            case FIGHT_PROP_CRITICAL_HURT -> weights.criticalDamage;
            case FIGHT_PROP_CHARGE_EFFICIENCY -> weights.energyRecharge;
            case FIGHT_PROP_ELEMENT_MASTERY -> weights.elementalMastery;
            case FIGHT_PROP_ATTACK_PERCENT,
                    FIGHT_PROP_HP_PERCENT,
                    FIGHT_PROP_DEFENSE_PERCENT -> weights.percentStat;
            case FIGHT_PROP_ATTACK, FIGHT_PROP_HP, FIGHT_PROP_DEFENSE -> weights.flatStat;
            default -> 1.00;
        };
    }

    /**
     * Uses the configured value-tier multipliers. When a depot has a different tier count than the
     * configured array, interpolate across the configured curve so the lowest and highest tiers
     * keep the same endpoints.
     */
    static double tierWeight(ReliquaryAffixData affix) {
        double[] configured = GAME.artifacts.rolls.valueTiers;
        if (configured == null || configured.length == 0) return 1.0;
        if (configured.length == 1) return configured[0];

        int tiers = GameDepot.getRelicAffixValueTierCount(affix);
        if (tiers <= 1) return configured[configured.length - 1];

        int tier = Math.max(0, Math.min(GameDepot.getRelicAffixValueTier(affix), tiers - 1));
        double scaled = tier * (configured.length - 1d) / (tiers - 1d);
        int lower = (int) Math.floor(scaled);
        int upper = Math.min(configured.length - 1, lower + 1);
        double fraction = scaled - lower;
        return configured[lower] + (configured[upper] - configured[lower]) * fraction;
    }
}
