package emu.grasscutter.game.dungeons;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.RewardPreviewData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.dungeons.DomainStatueClaimHelper.ClaimMode;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.packet.send.PacketGadgetAutoPickDropInfoNotify;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Petrified-tree claim implementation that does <b>not</b> replace {@link DungeonManager}.
 * Uses reflection only for the private rewardedPlayers set.
 */
public final class DomainStatueDropService {

    private DomainStatueDropService() {}

    public static boolean claim(
            Player player, DungeonManager dm, ClaimMode mode, int groupId) {
        if (player == null || dm == null) {
            return false;
        }
        if (mode == null) {
            mode = ClaimMode.NORMAL_1X;
        }
        if (!dm.isFinishedSuccessfully()) {
            Grasscutter.getLogger()
                    .warn("StatueDrop abort: not finished dungeon={}", dm.getDungeonData().getId());
            return false;
        }

        DungeonData dungeonData = dm.getDungeonData();
        IntSet rewarded = getRewardedPlayers(dm);
        if (rewarded == null) {
            Grasscutter.getLogger()
                    .warn("StatueDrop abort: reward-state access failed dungeon={}", dungeonData.getId());
            return false;
        }
        if (rewarded.contains(player.getUid())) {
            Grasscutter.getLogger()
                    .warn(
                            "StatueDrop abort: already rewarded uid={} dungeon={}",
                            player.getUid(),
                            dungeonData.getId());
            return false;
        }

        var preview = dungeonData.getRewardPreviewData();
        if (preview == null && dungeonData.getPassRewardPreviewID() > 0) {
            preview = GameData.getRewardPreviewDataMap().get(dungeonData.getPassRewardPreviewID());
        }
        boolean hasPreview =
                preview != null
                        && preview.getPreviewItems() != null
                        && preview.getPreviewItems().length > 0;
        try {
            DungeonDropLoader.ensureLoaded();
        } catch (RuntimeException e) {
            Grasscutter.getLogger().warn("StatueDrop: unable to reload DungeonDrop.json", e);
        }
        boolean hasConfiguredDrops =
                GameData.getDungeonDropDataMap() != null
                        && GameData.getDungeonDropDataMap().containsKey(dungeonData.getId());
        if (!hasPreview && dungeonData.getStatueDrop() <= 0 && !hasConfiguredDrops) {
            Grasscutter.getLogger()
                    .warn("StatueDrop abort: no reward source dungeon={}", dungeonData.getId());
            return false;
        }

        int rollTimes = Math.max(1, mode.rollTimes);
        List<GameItem> rewards;
        try {
            rewards = buildRewards(player, dm, dungeonData, preview, hasPreview, rollTimes);
        } catch (RuntimeException e) {
            Grasscutter.getLogger()
                    .warn("StatueDrop abort: invalid reward configuration dungeon={}", dungeonData.getId(), e);
            return false;
        }
        if (rewards == null || rewards.isEmpty()) {
            Grasscutter.getLogger()
                    .warn(
                            "StatueDrop abort: empty rewards dungeon={} statueDrop={}",
                            dungeonData.getId(),
                            dungeonData.getStatueDrop());
            return false;
        }

        try {
            ReliquaryDomainBonusHelper.appendToRewards(dm, rewards);
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("appendToRewards failed", t);
        }

        // Prepare and validate rewards before charging. A missing/invalid reward pool must
        // not consume Resin, Condensed Resin, Fragile Resin or Primogems.
        boolean granted;
        try {
            granted = DomainDropSafety.commitOnce(
                    dm,
                    rewarded,
                    player.getUid(),
                    rewards,
                    it -> it != null && it.getCount() > 0 && it.getItemId() > 0
                            && it.getItemData() != null,
                    () -> payCost(player, dungeonData, mode),
                    items -> player.getInventory().addItems(items, ActionReason.DungeonStatueDrop));
        } catch (RuntimeException e) {
            Grasscutter.getLogger()
                    .error("StatueDrop claim failed uid={} dungeon={}", player.getUid(), dungeonData.getId(), e);
            return false;
        }
        if (!granted) {
            Grasscutter.getLogger()
                    .warn("StatueDrop abort: claim rejected or payment failed uid={} dungeon={} mode={}",
                            player.getUid(), dungeonData.getId(), mode);
            return false;
        }
        player.sendPacket(new PacketGadgetAutoPickDropInfoNotify(rewards));

        try {
            dm.getScene()
                    .getScriptManager()
                    .callEvent(new ScriptArgs(groupId, EventType.EVENT_DUNGEON_REWARD_GET));
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("EVENT_DUNGEON_REWARD_GET failed", t);
        }

        Grasscutter.getLogger()
                .info(
                        "StatueDrop ok uid={} dungeon={} mode={} times={} items={}",
                        player.getUid(),
                        dungeonData.getId(),
                        mode,
                        rollTimes,
                        rewards.size());
        return true;
    }

    private static boolean payCost(Player player, DungeonData dungeonData, ClaimMode mode) {
        int baseResinCost =
                dungeonData.getStatueCostCount() != 0 ? dungeonData.getStatueCostCount() : 20;
        return switch (mode) {
            case CONDENSE -> {
                if (baseResinCost != 0 && baseResinCost != 20) {
                    yield false;
                }
                yield player.getResinManager().useCondensedResin(1);
            }
            case FRAGILE -> player.getInventory()
                    .payItem(DomainStatueClaimHelper.FRAGILE_RESIN_ITEM_ID, 1);
            case HCOIN -> player.getResinManager().payHcoinRewardClaim();
            case NORMAL_2X -> {
                if (dungeonData.getStatueCostID() != 0 && dungeonData.getStatueCostID() != 106) {
                    yield true;
                }
                yield player.getResinManager().useResin(40);
            }
            case NORMAL_1X -> {
                if (dungeonData.getStatueCostID() != 0 && dungeonData.getStatueCostID() != 106) {
                    yield true;
                }
                int cost = baseResinCost > 0 ? baseResinCost : 20;
                yield player.getResinManager().useResin(cost);
            }
        };
    }

    private static List<GameItem> buildRewards(
            Player player,
            DungeonManager dm,
            DungeonData dungeonData,
            RewardPreviewData preview,
            boolean hasPreview,
            int rollTimes) {
        List<GameItem> rewards = null;
        int dungeonId = dungeonData.getId();
        boolean hasDungeonDrop =
                GameData.getDungeonDropDataMap() != null
                        && GameData.getDungeonDropDataMap().containsKey(dungeonId);
        if (hasDungeonDrop) {
            rewards = rollDungeonDropJson(dm, dungeonData, rollTimes);
        }
        int statueDrop = dungeonData.getStatueDrop();
        if ((rewards == null || rewards.isEmpty()) && statueDrop > 0) {
            rewards = rollStatueDropTable(player, statueDrop, rollTimes);
        }
        if ((rewards == null || rewards.isEmpty()) && hasDungeonDrop) {
            rewards = rollDungeonDropJson(dm, dungeonData, rollTimes);
        }
        if ((rewards == null || rewards.isEmpty()) && hasPreview) {
            rewards = new ArrayList<>();
            for (ItemParamData param : preview.getPreviewItems()) {
                if (param != null && param.getId() > 0) {
                    rewards.add(new GameItem(param.getId(), Math.max(param.getCount(), 1) * rollTimes));
                }
            }
        }
        return rewards;
    }

    private static List<GameItem> rollStatueDropTable(Player player, int statueDrop, int rollTimes) {
        if (rollTimes <= 1) {
            return player.getServer().getDropSystem().handleDungeonRewardDrop(statueDrop, false);
        }
        if (rollTimes == 2) {
            return player.getServer().getDropSystem().handleDungeonRewardDrop(statueDrop, true);
        }
        List<GameItem> rewards = new ArrayList<>();
        List<GameItem> first =
                player.getServer().getDropSystem().handleDungeonRewardDrop(statueDrop, true);
        List<GameItem> extra =
                player.getServer().getDropSystem().handleDungeonRewardDrop(statueDrop, false);
        if (first != null) {
            rewards.addAll(first);
        }
        if (extra != null) {
            rewards.addAll(extra);
        }
        return rewards;
    }

    private static List<GameItem> rollDungeonDropJson(
            DungeonManager dm, DungeonData dungeonData, int rollTimes) {
        int times = Math.max(1, rollTimes);
        List<GameItem> rewards = new ArrayList<>();
        int dungeonId = dungeonData.getId();
        if (!GameData.getDungeonDropDataMap().containsKey(dungeonId)) {
            return rewards;
        }
        List<DungeonDropEntry> dropEntries = GameData.getDungeonDropDataMap().get(dungeonId);
        DomainDropSafety.validatePool(dungeonId, dropEntries);
        for (var entry : dropEntries) {
            int start = entry.getCounts().get(0);
            int end = entry.getCounts().get(entry.getCounts().size() - 1);
            var candidateAmounts = IntStream.range(start, end + 1).boxed().collect(Collectors.toList());

            int amount = 0;
            for (int t = 0; t < times; t++) {
                amount += Utils.drawRandomListElement(candidateAmounts, entry.getProbabilities());
            }
            if (entry.isMpDouble() && dm.getScene().getPlayerCount() > 1) {
                amount *= 2;
            }
            if (entry.getItems().size() == 1) {
                rewards.add(new GameItem(entry.getItems().get(0), amount));
            } else {
                for (int i = 0; i < amount; i++) {
                    int itemId =
                            Utils.drawRandomListElement(entry.getItems(), entry.getItemProbabilities());
                    rewards.add(new GameItem(itemId, 1));
                }
            }
        }
        return rewards;
    }

    @SuppressWarnings("unchecked")
    private static IntSet getRewardedPlayers(DungeonManager dm) {
        try {
            Field field = DungeonManager.class.getDeclaredField("rewardedPlayers");
            field.setAccessible(true);
            return (IntSet) field.get(dm);
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("StatueDrop: cannot access rewardedPlayers", t);
            return null;
        }
    }
}
