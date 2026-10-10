package emu.grasscutter.game.dungeons;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.RewardPreviewData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.dungeons.DomainStatueClaimHelper.ClaimMode;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.inventory.InventoryAddPolicy;
import emu.grasscutter.game.inventory.InventoryGrantBuilder;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.packet.send.PacketGadgetAutoPickDropInfoNotify;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;

/** Petrified-tree claim implementation shared with {@link DungeonManager}. */
public final class DomainStatueDropService {

    private DomainStatueDropService() {}

    public static boolean claim(
            Player player, DungeonManager dm, ClaimMode mode, int groupId) {
        return claim(player, dm, mode, groupId, mode == null ? 1 : mode.rollTimes);
    }

    // Preserve the legacy DungeonManager condensed-claim 2x multiplier while both entry
    // points share the same reward safety and payment logic.
    static boolean claim(
            Player player, DungeonManager dm, ClaimMode mode, int groupId, int rewardRollTimes) {
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
        IntSet rewarded = dm.getRewardedPlayersForClaims();
        synchronized (dm) {
            if (rewarded.contains(player.getUid())) {
                Grasscutter.getLogger()
                        .warn(
                                "StatueDrop abort: already rewarded uid={} dungeon={}",
                                player.getUid(),
                                dungeonData.getId());
                return false;
            }
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

        final ClaimMode paymentMode = mode;
        int rollTimes = Math.max(1, rewardRollTimes);
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

        // The inventory owns the entire batch admission and insertion. Domain logic only
        // guards claim eligibility and decides which consumable resource pays for the claim.
        boolean granted;
        Inventory inventory = player.getInventory();
        try {
            granted = DomainDropSafety.commitOnce(
                    dm,
                    rewarded,
                    player.getUid(),
                    rewards,
                    (items, confirmPayment) -> {
                        // Recheck completion under the DungeonManager lock before charging.
                        if (!dm.isFinishedSuccessfully()) {
                            return false;
                        }
                        return inventory.addItems(
                                        items,
                                        ActionReason.DungeonStatueDrop,
                                        InventoryAddPolicy.ALL_OR_NOTHING,
                                        () -> payCost(player, dungeonData, paymentMode),
                                        confirmPayment)
                                .allAccepted();
                    });
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
            try {
                rewards = rollDungeonDropJson(dm, dungeonData, rollTimes);
            } catch (IllegalArgumentException invalidProxy) {
                Grasscutter.getLogger()
                        .warn("StatueDrop: rejecting DungeonDrop.json proxy dungeon={}", dungeonId, invalidProxy);
                // A malformed or mismatched proxy must not pay out preview placeholders.
                // Use the source-bound drop root only if it exists and can yield rewards.
                int nativeRoot = dungeonData.getStatueDrop();
                return nativeRoot > 0
                        ? rollStatueDropTable(player, nativeRoot, rollTimes)
                        : null;
            }
        }
        int statueDrop = dungeonData.getStatueDrop();
        if ((rewards == null || rewards.isEmpty()) && statueDrop > 0) {
            rewards = rollStatueDropTable(player, statueDrop, rollTimes);
            // A configured native root with no resolved rewards must not silently turn into
            // an advertised preview that omits the actual domain material/artifact drops.
            if (rewards == null || rewards.isEmpty()) {
                return rewards;
            }
        }
        // A configured proxy that rolled no items is not an excuse to grant
        // unrelated preview placeholders. Refuse before charging instead.
        if ((rewards == null || rewards.isEmpty()) && !hasDungeonDrop && hasPreview) {
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
        int dungeonId = dungeonData.getId();
        var entries = GameData.getDungeonDropDataMap().get(dungeonId);
        if (entries == null) {
            return List.of();
        }
        List<ItemParamData> rolled = DomainDropRoller.roll(
                dungeonId,
                dungeonData.getSubType(),
                entries,
                rollTimes,
                dm.getScene().getPlayerCount() > 1);
        List<GameItem> rewards = new ArrayList<>();
        for (ItemParamData reward : rolled) {
            rewards.addAll(InventoryGrantBuilder.create(
                    GameData.getItemDataMap().get(reward.getId()), reward.getCount(), 1));
        }
        return rewards;
    }

}
