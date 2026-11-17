package emu.grasscutter.game.combine;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.CombineData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.quest.LiyuePerfumeQuestSupport;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;

import it.unimi.dsi.fastutil.ints.*;

import java.util.*;
import java.util.stream.Collectors;

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
        LiyuePerfumeQuestSupport.sendFormulas(player);
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
                .debug(
                        "Combine request from uid {}: combineId={}, count={}",
                        player.getUid(),
                        cid,
                        count);

        if (count <= 0) {
            player.sendPacket(new PacketCombineRsp(Retcode.RET_COMBINE_COUNT_TOO_LARGE_VALUE));
            return null;
        }

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

        if (!player.getUnlockedCombines().contains(cid)) {
            player.getUnlockedCombines().add(cid);
        }

        List<ItemParamData> material = buildMaterialCost(combineData);

        long total = (long) combineData.getResultItemCount() * count;
        var output = GameData.getItemDataMap().get(combineData.getResultItemId());
        if (total <= 0 || total > Integer.MAX_VALUE || output == null) {
            player.sendPacket(new PacketCombineRsp(Retcode.RET_COMBINE_COUNT_TOO_LARGE_VALUE));
            return null;
        }
        int resultCount = (int) total;
        // 产物能完整入包后才扣材料。
        var granted =
                player.getInventory()
                        .addItems(
                                List.of(new GameItem(output, resultCount)),
                                ActionReason.Combine,
                                emu.grasscutter.game.inventory.InventoryAddPolicy.ALL_OR_NOTHING,
                                () ->
                                        player.getInventory()
                                                .payItems(material, count, ActionReason.Combine),
                                () -> {});
        if (!granted.allAccepted()) {
            player.sendPacket(new PacketCombineRsp(Retcode.RET_ITEM_EXCEED_LIMIT_VALUE));
            return null;
        }

        Grasscutter.getLogger()
                .debug(
                        "Combine success for uid {}: combineId={}, resultItem={} x{}",
                        player.getUid(),
                        cid,
                        combineData.getResultItemId(),
                        resultCount);

        CombineResult result = new CombineResult();
        result.setMaterial(scaleItems(material, count));
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

    private static List<ItemParamData> scaleItems(List<ItemParamData> items, int count) {
        return items.stream()
                .map(item -> new ItemParamData(item.getId(), item.getCount() * count))
                .collect(Collectors.toList());
    }

    public synchronized void decomposeReliquaries(
            Player player, int configId, int count, List<Long> input) {
        List<Integer> possibleDrops = reliquaryDecomposeData.get(configId);
        if (possibleDrops == null) {
            player.sendPacket(
                    new PacketReliquaryDecomposeRsp(Retcode.RET_RELIQUARY_DECOMPOSE_PARAM_ERROR));
            return;
        }

        if (input.size() != count * 3) {
            player.sendPacket(
                    new PacketReliquaryDecomposeRsp(Retcode.RET_RELIQUARY_DECOMPOSE_PARAM_ERROR));
            return;
        }

        for (long guid : input) {
            var relic = player.getInventory().getItemByGuid(guid);
            if (relic == null
                    || emu.grasscutter.game.systems.ReliquarySystem.pending(player, relic)) {
                player.sendPacket(
                        new PacketReliquaryDecomposeRsp(Retcode.RET_RELIQUARY_DECOMPOSE_PARAM_ERROR));
                return;
            }
        }

        for (long guid : input) {
            player.getInventory().removeItem(guid);
        }

        List<Long> resultItems = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int itemId = Utils.drawRandomListElement(possibleDrops);
            GameItem newReliquary = new GameItem(itemId, 1);

            player.getInventory().addItem(newReliquary);
            resultItems.add(newReliquary.getGuid());
        }

        player.sendPacket(new PacketReliquaryDecomposeRsp(resultItems));
    }
}
