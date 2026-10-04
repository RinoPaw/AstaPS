package emu.grasscutter.game.managers;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.CityLevelupData;
import emu.grasscutter.data.excels.RewardData;
import emu.grasscutter.data.excels.StatuePromoteData;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/** Resolves statue/city offering rewards directly from the referenced native RewardData rows. */
public final class StatueOfferRewardHelper {
    private StatueOfferRewardHelper() {}

    /** Rewards granted when the city's SotS reaches {@code level} (after level-up). */
    public static Int2IntMap rewardsForLevel(int cityId, int level) {
        Int2IntMap rewards = new Int2IntOpenHashMap();

        StatuePromoteData promote = GameData.getStatuePromoteData(cityId, level);
        if (promote != null && promote.getCostItems() != null && promote.getCostItems().length > 0) {
            int[] rewardIds = promote.getRewardIdList();
            if (rewardIds != null) {
                for (int rewardId : rewardIds) {
                    addRewardItems(rewards, rewardId);
                }
            }
            return rewards;
        }

        CityLevelupData cityLevelup = GameData.getCityLevelupData(cityId, level);
        if (cityLevelup != null && cityLevelup.getRewardId() > 0) {
            addRewardItems(rewards, cityLevelup.getRewardId());
        }
        return rewards;
    }

    private static void addRewardItems(Int2IntMap dest, int rewardId) {
        RewardData reward = GameData.getRewardDataMap().get(rewardId);
        if (reward == null || reward.getRewardItemList() == null) {
            return;
        }

        for (var param : reward.getRewardItemList()) {
            if (param == null || param.getId() <= 0 || param.getCount() <= 0) {
                continue;
            }
            dest.put(param.getId(), dest.getOrDefault(param.getId(), 0) + param.getCount());
        }
    }
}
