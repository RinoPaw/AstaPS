package emu.grasscutter.game.battlepass;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.common.ItemUseData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.RewardData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.InventoryGrantBuilder;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.game.props.ItemUseOp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolve MATERIAL_SELECTABLE_CHEST BP rewards into real inventory items.
 *
 * <p>Client option_idx for specialty chests (e.g. 116018) is the 1-based slot id from
 * GroupedItemSelectionExcelConfig (can be up to 61), not necessarily bounded by a truncated
 * MaterialExcel useParam list. Only verified 7.1 group bounds permit such extensions.
 */
public final class BattlePassSelectChestHelper {
    // From 7.1 GroupedItemSelectionExcelConfigData, OCPCFBMOECN[0].EDDMCFGILPC.
    // These are client-visible indices, NOT arbitrary neighboring reward IDs.
    private static final Map<Integer, Integer> GROUPED_SELECTION_LIMITS = Map.of(
            115016, 24, 115017, 24, 115024, 24, 115025, 24,
            116016, 24, 116017, 24, 116018, 61);

    private BattlePassSelectChestHelper() {}

    public static boolean isSelectableReward(int rewardId) {
        return chestItemData(rewardId) != null;
    }

    public static ItemData chestItemData(int rewardId) {
        RewardData reward = GameData.getRewardDataMap().get(rewardId);
        if (reward == null || reward.getRewardItemList() == null) {
            return null;
        }
        for (ItemParamData ip : reward.getRewardItemList()) {
            if (ip == null || ip.getItemId() <= 0) {
                continue;
            }
            ItemData item = GameData.getItemDataMap().get(ip.getItemId());
            if (item != null && item.getMaterialType() == MaterialType.MATERIAL_SELECTABLE_CHEST) {
                return item;
            }
        }
        return null;
    }

    /**
     * Resolve, but do not grant, a selected chest. Ownership and inventory admission
     * are the caller's responsibility. Return an empty list if the choice is invalid.
     */
    public static List<GameItem> resolve(int rewardId, int optionIdx) {
        // The 7.1 grouped selection list uses indices no higher than 61. Keep room
        // for extended client data, but never extrapolate arbitrary attacker indices.
        if (rewardId <= 0 || optionIdx < 1 || optionIdx > 128) {
            return List.of();
        }
        ItemData chest = chestItemData(rewardId);
        if (chest == null) {
            return List.of();
        }
        ItemUseData use = firstUse(chest);
        if (use == null || use.getUseParam() == null || use.getUseParam().length < 1) {
            return List.of();
        }
        int chosen = resolveChoiceRewardOrItem(chest.getId(), use, optionIdx);
        if (chosen <= 0) {
            return List.of();
        }
        List<GameItem> result = new ArrayList<>();
        try {
            if (use.getUseOp() == ItemUseOp.ITEM_USE_ADD_SELECT_ITEM) {
                ItemData data = GameData.getItemDataMap().get(chosen);
                RewardData outer = GameData.getRewardDataMap().get(rewardId);
                int count = 1;
                if (outer != null && outer.getRewardItemList() != null) {
                    for (ItemParamData entry : outer.getRewardItemList()) {
                        if (entry != null && entry.getItemId() == chest.getId()) {
                            count = entry.getItemCount();
                            break;
                        }
                    }
                }
                result.addAll(InventoryGrantBuilder.create(data, count, 1));
            } else if (use.getUseOp() == ItemUseOp.ITEM_USE_GRANT_SELECT_REWARD) {
                RewardData reward = GameData.getRewardDataMap().get(chosen);
                if (reward == null || reward.getRewardItemList() == null
                        || reward.getRewardItemList().isEmpty()) {
                    return List.of();
                }
                for (ItemParamData entry : reward.getRewardItemList()) {
                    if (entry == null || entry.getItemId() <= 0 || entry.getItemCount() <= 0) {
                        return List.of();
                    }
                    result.addAll(InventoryGrantBuilder.create(
                            GameData.getItemDataMap().get(entry.getItemId()),
                            entry.getItemCount(), 1));
                }
            }
        } catch (RuntimeException invalid) {
            Grasscutter.getLogger().warn(
                    "TakeBP select: invalid reward={} choice={}", rewardId, optionIdx, invalid);
            return List.of();
        }
        return result;
    }

    /**
     * Map client option_idx to a reward id (GRANT_SELECT) or item id (ADD_SELECT).
     */
    private static int resolveChoiceRewardOrItem(int chestId, ItemUseData use, int optionIdx) {
        String raw = use.getUseParam()[0];
        if (raw == null || raw.isBlank()) {
            Grasscutter.getLogger().warn("TakeBP select: chest {} empty useParam[0]", chestId);
            return -1;
        }
        String[] parts = raw.split(",");
        List<Integer> choices = new ArrayList<>(parts.length + 16);
        for (String p : parts) {
            try {
                int v = Integer.parseInt(p.trim());
                if (v > 0) {
                    choices.add(v);
                }
            } catch (Exception ignored) {
                // skip malformed
            }
        }
        if (choices.isEmpty()) {
            Grasscutter.getLogger().warn("TakeBP select: chest {} no parseable choices", chestId);
            return -1;
        }

        // Apply the native grouped-option limit even to explicit useParam
        // entries; only unknown chest definitions use the generic upper bound.
        if (!isAllowedGroupedIndex(chestId, optionIdx)) {
            return -1;
        }
        if (optionIdx <= choices.size()) {
            return choices.get(optionIdx - 1);
        }

        // The 7.1 grouped selector can be longer than the MaterialExcel useParam
        // list. Restrict the compatibility mapping to that exact chest's client
        // option count, and only extend a proven contiguous prefix.
        if (!permitsGroupedExtension(chestId, optionIdx, choices)) {
            Grasscutter.getLogger().warn(
                    "TakeBP select: optionIdx {} is not configured for chest {}",
                    optionIdx, chestId);
            return -1;
        }

        long candidate = (long) choices.get(0) + optionIdx - 1;
        if (candidate > Integer.MAX_VALUE) {
            return -1;
        }
        int chosen = (int) candidate;
        if (use.getUseOp() == ItemUseOp.ITEM_USE_GRANT_SELECT_REWARD) {
            return GameData.getRewardDataMap().get(chosen) != null ? chosen : -1;
        }
        // ADD_SELECT_ITEM uses item IDs which need not be contiguous.
        return -1;
    }

    static boolean isAllowedGroupedIndex(int chestId, int index) {
        Integer limit = GROUPED_SELECTION_LIMITS.get(chestId);
        return index >= 1 && index <= 128 && (limit == null || index <= limit);
    }

    static boolean permitsGroupedExtension(int chestId, int optionIdx, List<Integer> explicit) {
        Integer maxIndex = GROUPED_SELECTION_LIMITS.get(chestId);
        if (maxIndex == null || optionIdx < 1 || optionIdx > maxIndex
                || explicit == null || explicit.isEmpty() || explicit.size() >= optionIdx) {
            return false;
        }
        long first = explicit.get(0);
        if (first <= 0) return false;
        for (int i = 0; i < explicit.size(); i++) {
            Integer id = explicit.get(i);
            if (id == null || (long) id != first + i) {
                return false;
            }
        }
        return true;
    }

    private static ItemUseData firstUse(ItemData chest) {
        if (chest.getItemUse() == null) {
            return null;
        }
        for (ItemUseData u : chest.getItemUse()) {
            if (u == null || u.getUseOp() == null) {
                continue;
            }
            if (u.getUseOp() == ItemUseOp.ITEM_USE_NONE) {
                continue;
            }
            return u;
        }
        return null;
    }
}
