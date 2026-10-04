package emu.grasscutter.config;

/** Global artifact generation settings stored in game.json. */
public final class ArtifactSettings {
    public RollWeights rolls = new RollWeights();

    public void normalize() {
        if (rolls == null) rolls = new RollWeights();
        rolls.normalize();
    }

    public static final class RollWeights {
        public double critical = 1.60;
        public double criticalDamage = 1.60;
        public double energyRecharge = 1.20;
        public double elementalMastery = 1.20;
        public double percentStat = 1.00;
        public double flatStat = 0.60;
        public double[] valueTiers = {0.40, 0.80, 1.20, 1.60};

        private void normalize() {
            critical = Math.max(0.0, critical);
            criticalDamage = Math.max(0.0, criticalDamage);
            energyRecharge = Math.max(0.0, energyRecharge);
            elementalMastery = Math.max(0.0, elementalMastery);
            percentStat = Math.max(0.0, percentStat);
            flatStat = Math.max(0.0, flatStat);
            if (critical
                            + criticalDamage
                            + energyRecharge
                            + elementalMastery
                            + percentStat
                            + flatStat
                    <= 0.0) {
                critical = 1.60;
                criticalDamage = 1.60;
                energyRecharge = 1.20;
                elementalMastery = 1.20;
                percentStat = 1.00;
                flatStat = 0.60;
            }

            if (valueTiers == null || valueTiers.length == 0) {
                valueTiers = defaultValueTiers();
                return;
            }
            double total = 0.0;
            for (int i = 0; i < valueTiers.length; i++) {
                valueTiers[i] = Math.max(0.0, valueTiers[i]);
                total += valueTiers[i];
            }
            if (total <= 0.0) valueTiers = defaultValueTiers();
        }

        private static double[] defaultValueTiers() {
            return new double[] {0.40, 0.80, 1.20, 1.60};
        }
    }
}
