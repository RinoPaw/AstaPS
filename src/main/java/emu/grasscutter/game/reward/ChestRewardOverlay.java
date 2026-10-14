package emu.grasscutter.game.reward;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.inventory.GameItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only partition of a rolled native chest reward and operator-owned fixed amounts.
 *
 * <p>Ownership is established by every field named in the resolved TOML rules, including
 * explicit zero clears. The native roll is still needed for unowned, random or special items.
 * This class does not mutate native items, grant loot, or mark a chest as opened.
 */
public final class ChestRewardOverlay {
    private ChestRewardOverlay() {}

    public record Plan(
            List<GameItem> nativeItems, List<ItemParamData> configuredItems) {
        public Plan {
            nativeItems = List.copyOf(nativeItems);
            configuredItems = List.copyOf(configuredItems);
        }
    }

    /**
     * Keep each unowned native item with its original rolled stats, identity-independent.
     * Native entries whose item IDs are owned by any matching config rule are removed,
     * even when the final configured amount is zero.
     */
    public static Plan plan(ChestRewardConfig.Match match, List<GameItem> nativeRoll) {
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(nativeRoll, "nativeRoll");

        var nativeItems = new ArrayList<GameItem>();
        for (GameItem item : nativeRoll) {
            if (item == null || item.getItemId() <= 0 || item.getCount() <= 0) {
                throw new IllegalArgumentException("Native chest roll contains an invalid item");
            }
            if (!match.ownedItemIds().contains(item.getItemId())) {
                // A future grant must not reuse an original owner's GUID or mutate a roll
                // belonging to a separate delivery attempt.
                nativeItems.add(item.copyForGrant());
            }
        }
        return new Plan(nativeItems, match.rewards());
    }
}
