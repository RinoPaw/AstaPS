package emu.grasscutter.game.reward;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.drop.DropSystem;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.gadget.chest.StandardChestClassifier;
import emu.grasscutter.game.entity.gadget.chest.StandardChestClassifier.Classification;
import emu.grasscutter.game.world.WorldAreaIndex;
import emu.grasscutter.game.world.WorldAreaIndex.AreaIds;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only preflight for ordinary exploration chest direct rewards.
 *
 * <p>A preview never proves native/random drop delivery, and it must not be used
 * to open a chest or record a claim. The live GadgetChest payout remains unchanged.
 */
public final class StandardChestRewardResolver {
    private StandardChestRewardResolver() {}

    /** The selected rule and its ownership are from one immutable config snapshot. */
    public record Preview(
            ChestRewardConfig.Kind kind,
            int sceneId,
            AreaIds area,
            ChestRewardConfig.Match match) {
        public Preview {
            Objects.requireNonNull(match, "match");
        }

        public String lastSelector() {
            return match.selector();
        }

        public List<ItemParamData> rewards() {
            return match.rewards();
        }
    }

    /** In-memory proposal: no entity, inventory or claim-state side effects. */
    public record Staged(
            Preview preview, DropSystem.ChestRoll nativeRoll, ChestRewardOverlay.Plan overlay) {}

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
        return Optional.of(new Preview(kind, sceneId, area, match));
    }

    /**
     * Native ground-drop roots must retain their pickup behavior. Only known authored
     * identities and direct-inventory roots may enter the current journaled runtime path.
     */
    public static boolean supportsInventoryClaim(Staged staged, int groupId, int configId) {
        return staged != null
                && staged.nativeRoll() != null
                && !staged.nativeRoll().fallToGround()
                && staged.preview() != null
                && groupId > 0 && configId > 0;
    }

    /** Compose an already rolled native result with the exact config preview snapshot. */
    public static Staged stage(Preview preview, DropSystem.ChestRoll nativeRoll) {
        Objects.requireNonNull(preview, "preview");
        Objects.requireNonNull(nativeRoll, "nativeRoll");
        return new Staged(
                preview, nativeRoll, ChestRewardOverlay.plan(preview.match(), nativeRoll.items()));
    }

    /**
     * Full read-only pipeline. Never call repeatedly after an ambiguous delivery:
     * the native random roll must be retained with the eventual claim receipt.
     */
    public static Optional<Staged> stage(EntityGadget chest, DropSystem dropSystem) {
        Objects.requireNonNull(dropSystem, "dropSystem");
        var preview = preview(chest);
        if (preview.isEmpty() || chest.getMetaGadget() == null) {
            return Optional.empty();
        }
        String tag = chest.getMetaGadget().drop_tag;
        return dropSystem.rollChestTag(tag, 1).map(roll -> stage(preview.orElseThrow(), roll));
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
