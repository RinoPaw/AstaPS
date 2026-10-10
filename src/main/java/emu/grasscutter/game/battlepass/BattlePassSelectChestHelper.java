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

/**
 * Resolve MATERIAL_SELECTABLE_CHEST BP rewards into real inventory items.
 *
 * <p>Client option_idx for specialty chests (e.g. 116018) is the 1-based slot id from
 * GroupedItemSelectionExcelConfig (can be up to 61), not necessarily bounded by a truncated
 * MaterialExcel useParam list. We extend consecutive GRANT_SELECT reward ids when present.
 */
public final class BattlePassSelectChestHelper {
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

        // Auto-extend consecutive reward ids when MaterialExcel list is truncated vs client UI.
        if (use.getUseOp() == ItemUseOp.ITEM_USE_GRANT_SELECT_REWARD) {
            int last = choices.get(choices.size() - 1);
            while (true) {
                int next = last + 1;
                if (GameData.getRewardDataMap().get(next) == null) {
                    break;
                }
                choices.add(next);
                last = next;
                if (choices.size() > 128) {
                    break;
                }
            }
        }

        if (optionIdx <= choices.size()) {
            return choices.get(optionIdx - 1);
        }

        // Fallback: treat optionIdx as 1-based offset from the first reward/item id.
        int extrapolated = choices.get(0) + (optionIdx - 1);
        if (use.getUseOp() == ItemUseOp.ITEM_USE_GRANT_SELECT_REWARD) {
            if (GameData.getRewardDataMap().get(extrapolated) != null) {
                Grasscutter.getLogger()
                        .info(
                                "TakeBP select: optionIdx {} beyond list ({}) chest {}, using reward {}",
                                optionIdx,
                                choices.size(),
                                chestId,
                                extrapolated);
                return extrapolated;
            }
        } else if (GameData.getItemDataMap().get(extrapolated) != null) {
            Grasscutter.getLogger()
                    .info(
                            "TakeBP select: optionIdx {} beyond list ({}) chest {}, using item {}",
                            optionIdx,
                            choices.size(),
                            chestId,
                            extrapolated);
            return extrapolated;
        }

        Grasscutter.getLogger()
                .warn(
                        "TakeBP select: optionIdx {} out of range {} for chest {} (extrapolated {} missing)",
                        optionIdx,
                        choices.size(),
                        chestId,
                        extrapolated);
        return -1;
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
