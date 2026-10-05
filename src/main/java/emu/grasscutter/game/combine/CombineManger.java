package emu.grasscutter.game.combine;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.CombineData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;

public class CombineManger extends BaseGameSystem {
    private static final Int2ObjectMap<List<Integer>> reliquaryDecomposeData =
            new Int2ObjectOpenHashMap<>();

    public CombineManger(GameServer server) {
        super(server);
    }

    public static void initialize() {
        try {
            DataLoader.loadList("ReliquaryDecompose.json", ReliquaryDecomposeEntry.class)
                    .forEach(
                            entry -> {
                                reliquaryDecomposeData.put(entry.getConfigId(), entry.getItems());
                            });
            Grasscutter.getLogger()
                    .debug("Loaded {} reliquary decompose entries.", reliquaryDecomposeData.size());
        } catch (Exception ex) {
            Grasscutter.getLogger().error("Unable to load reliquary decompose data.", ex);
        }
    }

    /** Unlock all combine recipes for private server convenience. */
    public void onPlayerLogin(Player player) {
        for (CombineData data : GameData.getCombineDataMap().values()) {
            player.getUnlockedCombines().add(data.getCombineId());
        }
        Grasscutter.getLogger()
                .debug(
                        "Unlocked {} combine recipes for player {}.",
                        player.getUnlockedCombines().size(),
                        player.getUid());
    }

    public boolean unlockCombineDiagram(Player player, int combineId) {
        if (!player.getUnlockedCombines().add(combineId)) {
            return false;
        }
        player.sendPacket(new PacketCombineFormulaDataNotify(combineId));
        return true;
    }

    public CombineResult combineItem(Player player, int cid, int count) {
        Grasscutter.getLogger()
                .debug("Combine request from uid {}: combineId={}, count={}", player.getUid(), cid, count);

        CombineData combineData = GameData.getCombineDataMap().get(cid);
        if (combineData == null) {
            Grasscutter.getLogger().warn("Unknown combineId {} for uid {}", cid, player.getUid());
            player.sendPacket(new PacketCombineRsp());
            return null;
        }

        if (combineData.getPlayerLevel() > player.getLevel()) {
            player.sendPacket(new PacketCombineRsp(Retcode.RET_PLAYER_LEVEL_LESS_THAN_VALUE));
            return null;
        }

        List<ItemParamData> material = buildMaterialCost(combineData);
        var plan =
                CombineSafety.plan(
                        count,
                        combineData.getResultItemCount(),
                        material.stream()
                                .map(item -> new CombineSafety.Cost(item.getId(), item.getCount()))
                                .toList());
        if (plan == null) {
            player.sendPacket(new PacketCombineRsp(Retcode.RET_COMBINE_COUNT_TOO_LARGE_VALUE));
            return null;
        }

        if (!player.getUnlockedCombines().contains(cid)) {
            player.getUnlockedCombines().add(cid);
        }

        List<ItemParamData> scaledMaterial =
                plan.costs().stream()
                        .map(cost -> new ItemParamData(cost.itemId(), cost.count()))
                        .toList();

        // All arithmetic and aggregation is complete before the synchronized Inventory payment.
        // Passing quantity=1 avoids a second unchecked multiplication in payItems().
        if (!player.getInventory().payItems(scaledMaterial, 1, ActionReason.Combine)) {
            player.sendPacket(new PacketCombineRsp(Retcode.RET_ITEM_COMBINE_COUNT_NOT_ENOUGH_VALUE));
            return null;
        }

        if (combineData.getScoinCost() > 0) {
            player.sendPacket(new PacketPlayerPropNotify(player, PlayerProperty.PROP_PLAYER_SCOIN));
        }

        int resultCount = plan.resultCount();
        player.getInventory().addItem(combineData.getResultItemId(), resultCount, ActionReason.Combine);

        Grasscutter.getLogger()
                .debug(
                        "Combine success for uid {}: combineId={}, resultItem={} x{}",
                        player.getUid(),
                        cid,
                        combineData.getResultItemId(),
                        resultCount);

        CombineResult result = new CombineResult();
        result.setMaterial(scaledMaterial);
        result.setResult(
                List.of(new ItemParamData(combineData.getResultItemId(), resultCount)));
        result.setExtra(List.of());
        result.setBack(List.of());

        return result;
    }

    private static List<ItemParamData> buildMaterialCost(CombineData combineData) {
        List<ItemParamData> material = new ArrayList<>(combineData.getMaterialItems());
        if (combineData.getScoinCost() > 0) {
            material.add(new ItemParamData(202, combineData.getScoinCost()));
        }
        return material;
    }

    public void decomposeReliquaries(Player player, int configId, int count, List<Long> input) {
        List<Integer> possibleDrops = reliquaryDecomposeData.get(configId);
        if (possibleDrops == null || possibleDrops.isEmpty()
                || !ReliquaryDecomposeSafety.validShape(count, input)) {
            sendReliquaryDecomposeError(player);
            return;
        }

        var inventory = player.getInventory();
        synchronized (inventory) {
            List<GameItem> inputs = new ArrayList<>(input.size());
            for (long guid : input) {
                GameItem item = inventory.getItemByGuid(guid);
                if (!isValidReliquaryInput(item)) {
                    sendReliquaryDecomposeError(player);
                    return;
                }
                inputs.add(item);
            }

            // The inventory monitor is held from validation through removal. Every GUID is unique and
            // every reliquary is a single-count equip item, so the validated set cannot change through
            // Inventory's synchronized mutation paths between these two phases.
            for (GameItem item : inputs) {
                if (!inventory.removeItem(item.getGuid())) {
                    sendReliquaryDecomposeError(player);
                    return;
                }
            }
        }

        List<Long> resultItems = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int itemId = Utils.drawRandomListElement(possibleDrops);
            GameItem newReliquary = new GameItem(itemId, 1);

            if (!player.getInventory().addItem(newReliquary)) {
                sendReliquaryDecomposeError(player);
                return;
            }
            resultItems.add(newReliquary.getGuid());
        }

        player.sendPacket(new PacketReliquaryDecomposeRsp(resultItems));
    }

    private static boolean isValidReliquaryInput(GameItem item) {
        return item != null
                && item.getItemData() != null
                && ReliquaryDecomposeSafety.eligible(
                        item.getItemType() == ItemType.ITEM_RELIQUARY,
                        item.getItemData().getRankLevel(),
                        item.isLocked(),
                        item.isEquipped());
    }

    private static void sendReliquaryDecomposeError(Player player) {
        player.sendPacket(
                new PacketReliquaryDecomposeRsp(Retcode.RET_RELIQUARY_DECOMPOSE_PARAM_ERROR));
    }
}
