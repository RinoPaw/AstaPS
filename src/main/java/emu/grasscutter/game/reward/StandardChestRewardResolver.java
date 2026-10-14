package emu.grasscutter.game.reward;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.gadget.chest.StandardChestClassifier;
import emu.grasscutter.game.entity.gadget.chest.StandardChestClassifier.Classification;
import emu.grasscutter.game.world.WorldAreaIndex;
import emu.grasscutter.game.world.WorldAreaIndex.AreaIds;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only preflight for ordinary exploration chest direct rewards.
 *
 * <p>A preview never proves native/random drop delivery, and it must not be used
 * to open a chest or record a claim. The fallback world-chest helper uses the
 * resolved fixed amounts; claim lifecycle remains owned by GadgetChest.
 */
public final class StandardChestRewardResolver {
    private StandardChestRewardResolver() {}

    public record Preview(
            ChestRewardConfig.Kind kind,
            int sceneId,
            AreaIds area,
            String lastSelector,
            List<ItemParamData> rewards,
            Map<Integer, Integer> amounts) {
        public Preview {
            rewards = List.copyOf(rewards);
            amounts = Map.copyOf(amounts);
        }
    }

    /**
     * Unknown level-1 area fails closed instead of selecting the higher-Primogem
     * default. Unknown level-2 area may still use a verified level-1 parent rule.
     */
    public static Optional<Preview> resolve(
            Classification classification, int sceneId, AreaIds area, ChestRewardConfig config) {
        Objects.requireNonNull(config, "config");
        if (classification == null
                || !classification.isStandard()
                || sceneId <= 0
                || area == null
                || area.level1() <= 0
                || area.level2() < 0) {
            return Optional.empty();
        }
        var kind = ChestRewardConfig.Kind.valueOf(classification.kind().name());
        var match = config.resolve(kind, sceneId, area.level1(), area.level2());
        return Optional.of(
                new Preview(kind, sceneId, area, match.selector(), match.rewards(), match.amounts()));
    }

    /** Inspects real gadget metadata and position, without writing player state. */
    public static Optional<Preview> preview(EntityGadget chest) {
        var classification = StandardChestClassifier.classify(chest);
        if (!classification.isStandard()
                || chest.getScene() == null
                || chest.getPosition() == null) {
            return Optional.empty();
        }
        int sceneId = chest.getScene().getId();
        var pos = chest.getPosition();
        var area = WorldAreaIndex.forScene(sceneId).areaAt(
                pos.getX(), pos.getY(), pos.getZ());
        return resolve(classification, sceneId, area, RewardConfig.current().chest());
    }
}
