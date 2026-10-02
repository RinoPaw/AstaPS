package emu.grasscutter.game.battlepass;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.common.ItemUseData;
import emu.grasscutter.data.excels.BattlePassRewardData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.RewardData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ItemUseOp;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.BattlePassRewardTagOuterClass;
import emu.grasscutter.net.proto.BattlePassRewardTakeOptionOuterClass;
import emu.grasscutter.net.proto.BattlePassUnlockStatusOuterClass;
import emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify;
import emu.grasscutter.server.packet.send.PacketBeyondBattlePassCurScheduleUpdateNotify;
import emu.grasscutter.server.packet.send.PacketTakeBattlePassRewardRsp;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class BattlePassCompatHelper {
    private static final ConcurrentHashMap<Integer, Integer> SELECTED_PLAN =
            new ConcurrentHashMap<>();
    private static final int[] BP_STACK_TRIPLE_ITEMS = {202, 104001, 104002, 104003, 104013};

    private BattlePassCompatHelper() {}

    public static int getSelectedPlan(Player player) {
        return player == null ? 1 : SELECTED_PLAN.getOrDefault(player.getUid(), 1);
    }

    public static void setSelectedPlan(Player player, int plan) {
        if (player != null) {
            SELECTED_PLAN.put(player.getUid(), plan > 0 ? plan : 1);
        }
    }

    public static int rewardDataKey(int level) {
        return 100 + level;
    }

    private static int scaledCount(int itemId, int count) {
        if (count <= 0) {
            return count;
        }
        for (int scaledItemId : BP_STACK_TRIPLE_ITEMS) {
            if (scaledItemId == itemId) {
                return count * 3;
            }
        }
        return count;
    }

    public static boolean isRewardAllowed(
            BattlePassManager battlePassManager, int level, int rewardId) {
        if (rewardId <= 0 || level <= 0) {
            return false;
        }

        for (int plan = 1; plan <= 4; plan++) {
            BattlePassRewardData rewardData =
                    GameData.getBattlePassRewardDataMap().get(plan * 100 + level);
            if (rewardData == null) {
                continue;
            }
            if (rewardData.getFreeRewardIdList() != null
                    && rewardData.getFreeRewardIdList().contains(rewardId)) {
                return true;
            }
            if (battlePassManager.isPaid()
                    && rewardData.getPaidRewardIdList() != null
                    && rewardData.getPaidRewardIdList().contains(rewardId)) {
                return true;
            }
        }

        return GameData.getRewardDataMap().containsKey(rewardId);
    }

    public static void takeReward(
            BattlePassManager battlePassManager,
            List<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption> requested) {
        if (battlePassManager != null
                && battlePassManager.getPlayer() != null
                && requested != null) {
            var accepted =
                    new ArrayList<BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption>();
            for (var option : requested) {
                if (option == null || option.getTag() == null) {
                    continue;
                }

                int rewardId = option.getTag().getRewardId();
                int level = option.getTag().getLevel();
                if (rewardId != 0 && level <= battlePassManager.getLevel()) {
                    if (battlePassManager.getTakenRewards().containsKey(rewardId)) {
                        Grasscutter.getLogger()
                                .info(
                                        "BattlePass already taken uid={} rewardId={}",
                                        battlePassManager.getPlayer().getUid(),
                                        rewardId);
                        continue;
                    }
                    if (!isRewardAllowed(battlePassManager, level, rewardId)) {
                        Grasscutter.getLogger().info("Not in rewards list: {}", rewardId);
                        continue;
                    }
                    accepted.add(option);
                    continue;
                }

                Grasscutter.getLogger()
                        .info(
                                "BattlePass claim skip uid={} rewardId={} level={} playerLv={}",
                                battlePassManager.getPlayer().getUid(),
                                rewardId,
                                level,
                                battlePassManager.getLevel());
            }

            if (accepted.isEmpty()) {
                Grasscutter.getLogger()
                        .info(
                                "BattlePass claim empty uid={} requested={}",
                                battlePassManager.getPlayer().getUid(),
                                requested.size());
            }

            List<GameItem> items = null;
            if (!accepted.isEmpty()) {
                items = new ArrayList<>();
                for (var option : accepted) {
                    var tag = option.getTag();
                    int optionIndex = option.getOptionIdx();
                    RewardData rewardData = GameData.getRewardDataMap().get(tag.getRewardId());
                    if (rewardData == null) {
                        continue;
                    }

                    for (ItemParamData itemParamData : rewardData.getRewardItemList()) {
                        if (itemParamData == null || itemParamData.getItemId() <= 0) {
                            continue;
                        }
                        ItemData itemData = GameData.getItemDataMap().get(itemParamData.getItemId());
                        if (itemData == null) {
                            continue;
                        }
                        if (itemData.getMaterialType() == MaterialType.MATERIAL_SELECTABLE_CHEST) {
                            takeRewardsFromSelectChest(
                                    itemData, optionIndex, itemParamData, items);
                            continue;
                        }
                        int count = scaledCount(itemParamData.getItemId(), itemParamData.getItemCount());
                        items.add(new GameItem(itemData, count));
                    }

                    var reward =
                            new BattlePassReward(
                                    tag.getLevel(),
                                    tag.getRewardId(),
                                    tag.getUnlockStatus()
                                            == BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus
                                                    .BattlePassUnlockStatus_BATTLE_PASS_UNLOCK_PAID);
                    battlePassManager.getTakenRewards().put(reward.getRewardId(), reward);
                }

                battlePassManager.save();
                battlePassManager.getPlayer().getInventory().addItems(items);
                battlePassManager
                        .getPlayer()
                        .sendPacket(new PacketBattlePassCurScheduleUpdateNotify(battlePassManager.getPlayer()));
                Grasscutter.getLogger()
                        .info(
                                "BattlePass claim ok uid={} granted={} items={}",
                                battlePassManager.getPlayer().getUid(),
                                accepted.size(),
                                items.size());
            }

            battlePassManager
                    .getPlayer()
                    .sendPacket(new PacketTakeBattlePassRewardRsp(requested, items));
        }

        try {
            if (battlePassManager != null && battlePassManager.getPlayer() != null) {
                battlePassManager
                        .getPlayer()
                        .sendPacket(
                                (BasePacket)
                                        new PacketBeyondBattlePassCurScheduleUpdateNotify(
                                                battlePassManager.getPlayer()));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void takeRewardsFromSelectChest(
            ItemData itemData,
            int optionIndex,
            ItemParamData itemParamData,
            List<GameItem> items) {
        if (itemData.getItemUse() == null || itemData.getItemUse().isEmpty()) {
            return;
        }

        ItemUseData itemUseData = itemData.getItemUse().get(0);
        if (itemUseData.getUseParam() == null || itemUseData.getUseParam().length < 1) {
            return;
        }

        String[] choices = itemUseData.getUseParam()[0].split(",");
        if (optionIndex < 1 || choices.length < optionIndex) {
            return;
        }

        int selectedId = Integer.parseInt(choices[optionIndex - 1].trim());
        if (itemUseData.getUseOp() == ItemUseOp.ITEM_USE_ADD_SELECT_ITEM) {
            ItemData selectedItem = GameData.getItemDataMap().get(selectedId);
            if (selectedItem != null) {
                items.add(
                        new GameItem(
                                selectedItem,
                                scaledCount(selectedId, itemParamData.getItemCount())));
            }
        } else if (itemUseData.getUseOp() == ItemUseOp.ITEM_USE_GRANT_SELECT_REWARD) {
            RewardData selectedReward = GameData.getRewardDataMap().get(selectedId);
            if (selectedReward == null) {
                return;
            }
            for (ItemParamData selectedParam : selectedReward.getRewardItemList()) {
                if (selectedParam == null || selectedParam.getItemId() <= 0) {
                    continue;
                }
                ItemData selectedItem = GameData.getItemDataMap().get(selectedParam.getItemId());
                if (selectedItem == null) {
                    continue;
                }
                items.add(
                        new GameItem(
                                selectedItem,
                                scaledCount(
                                        selectedParam.getItemId(), selectedParam.getItemCount())));
            }
        } else {
            Grasscutter.getLogger().error("Invalid chest type for BP reward.");
        }
    }

    public static void clearPlayerState(int uid) {
        SELECTED_PLAN.remove(uid);
    }

    public static int beginTime() {
        return 1785528000;
    }

    public static int endTime() {
        return 1795982399;
    }

    public static boolean setPaidFlag(BattlePassManager battlePassManager, boolean paid) {
        if (battlePassManager == null) {
            return false;
        }
        try {
            Field field = BattlePassManager.class.getDeclaredField("paid");
            field.setAccessible(true);
            field.setBoolean(battlePassManager, paid);
            return true;
        } catch (Exception exception) {
            Grasscutter.getLogger().error("BattlePass setPaid failed", exception);
            return false;
        }
    }

    public static void logBuilt(Player player, String packetName) {
        Grasscutter.getLogger()
                .info(
                        "BattlePassCompat {} uid={} schedule={}",
                        packetName,
                        player != null ? player.getUid() : 0,
                        6700);
    }
}
