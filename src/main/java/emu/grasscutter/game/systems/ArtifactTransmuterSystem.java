package emu.grasscutter.game.systems;

import dev.morphia.annotations.Entity;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.reliquary.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetReliquaryOfferingReqOuterClass.GetReliquaryOfferingReq;
import emu.grasscutter.net.proto.GetReliquaryOfferingRspOuterClass.GetReliquaryOfferingRsp;
import emu.grasscutter.net.proto.GetReliquaryPurchaseReqOuterClass.GetReliquaryPurchaseReq;
import emu.grasscutter.net.proto.GetReliquaryPurchaseRspOuterClass.GetReliquaryPurchaseRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.objects.WeightedList;

import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** 7.2 圣言自明机，制作与萃取共用玩家存档。 */
public final class ArtifactTransmuterSystem {
    public static final int GADGET_ITEM_ID = 220109, ELIXIR_ITEM_ID = 105005, MAX_PROGRESS = 100;
    private static final Set<Integer> LOGIN_OFFER_SENT = ConcurrentHashMap.newKeySet();
    private static final String PURCHASE = "CONST_VALUE_COMPOUND_SPE_RELIQUARY_PARAMS_1";
    private static final String OFFERING = "CONST_VALUE_COMPOUND_SPE_RELIQUARY_PARAMS_2";

    private ArtifactTransmuterSystem() {}

    public static void sendLoginNotifyOnce(Player player) {
        if (player != null && LOGIN_OFFER_SENT.add(player.getUid())) sendLoginNotify(player);
    }

    public static void clearLoginOfferFlag(int uid) {
        LOGIN_OFFER_SENT.remove(uid);
    }

    public static void ensureCycle(PlayerState state) {
        ensureCycle(state, System.currentTimeMillis() / 1000);
    }

    static void ensureCycle(PlayerState state, long now) {
        state.ensureCycle(now);
    }

    public static PlayerState state(Player player) {
        var state = player.getTransmuterState();
        if (state == null) {
            state = new PlayerState();
            // 旧版单独写文件的进度迁入玩家存档，原文件保留。
            var path = FileUtils.getDataPath("artifact_transmuter_state.json");
            if (Files.isRegularFile(path)) {
                try {
                    var root =
                            com.google.gson.JsonParser.parseString(Files.readString(path))
                                    .getAsJsonObject();
                    var old = root.get(Integer.toString(player.getUid()));
                    if (old != null && !old.isJsonNull())
                        state = new com.google.gson.Gson().fromJson(old, PlayerState.class);
                    if (!Double.isFinite(state.progress) || state.progress < 0)
                        throw new IllegalStateException("旧萃取进度无效");
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("无法迁移萃取进度", e);
                }
            }
            player.setTransmuterState(state);
        }
        ensureCycle(state);
        return state;
    }

    public static void saveState(Player player) {
        player.save();
    }

    public static int cycleEndTime(PlayerState state) {
        ensureCycle(state);
        return state.cycleEndUnix;
    }

    public static int remainingElixir(PlayerState state) {
        return Math.max(0, ReliquaryConfig.get().value(OFFERING, 4) - state.extractedThisCycle);
    }

    public static void ensureGadget(Player player) {
        if (GameData.getItemDataMap().containsKey(GADGET_ITEM_ID)
                && player.getInventory().getItemCountById(GADGET_ITEM_ID) == 0)
            player.getInventory().addItem(GADGET_ITEM_ID, 1, ActionReason.PlayerUpgradeReward);
    }

    public static void sendLoginNotify(Player player) {
        ensureGadget(player);
        sendDataNotify(player);
    }

    public static void sendDataNotify(Player player) {
        player.sendPacket(new PacketReliquaryOfferDataNotify(state(player)));
        ReliquaryDustSystem.sendDataNotify(player);
    }

    private static void send(
            Player player, int opcode, int sequence, com.google.protobuf.MessageLite data) {
        var packet = new BasePacket(opcode, sequence);
        packet.setData(data);
        player.sendPacket(packet);
    }

    public static void handleExtract(Player player, byte[] payload) {
        handleExtract(player, payload, 0);
    }

    public static void handleExtract(Player player, byte[] payload, int sequence) {
        var rsp = GetReliquaryOfferingRsp.newBuilder();
        try {
            var req = GetReliquaryOfferingReq.parseFrom(payload);
            rsp.setScheduleId(req.getScheduleId());
            synchronized (player.getInventory()) {
                var c = ReliquaryConfig.get();
                var st = state(player);
                var inv = player.getInventory();
                ReliquarySystem.require(
                        req.getScheduleId() == st.scheduleId
                                && st.extractedThisCycle < c.value(OFFERING, 4));
                ReliquarySystem.require(
                        req.getReliquaryGuidListCount() <= c.value(OFFERING, 2)
                                && req.getItemListCount() <= 64);
                var relics = new ArrayList<GameItem>();
                var seen = new HashSet<Long>();
                double gained = 0;
                var curve = c.values(OFFERING, 5);
                for (long guid : req.getReliquaryGuidListList()) {
                    var relic = inv.getItemByGuid(guid);
                    ReliquarySystem.require(
                            seen.add(guid)
                                    && relic != null
                                    && relic.getItemType() == ItemType.ITEM_RELIQUARY
                                    && !relic.isLocked()
                                    && relic.getEquipCharacter() == 0
                                    && !ReliquarySystem.pending(player, relic));
                    ReliquarySystem.require(
                            relic.getItemData().getRankLevel() == c.value(OFFERING, 0)
                                    && relic.getLevel() >= c.value(OFFERING, 1)
                                    && relic.getLevel() <= curve.size());
                    gained += curve.get(relic.getLevel() - 1);
                    relics.add(relic);
                }
                var costs = new TreeMap<Integer, Integer>();
                var materials = c.offeringMaterials();
                for (var item : req.getItemListList()) {
                    if (item.getCount() == 0) continue;
                    ReliquarySystem.require(
                            item.getCount() > 0 && materials.containsKey(item.getItemId()));
                    costs.merge(item.getItemId(), item.getCount(), Math::addExact);
                }
                for (var cost : costs.entrySet()) {
                    ReliquarySystem.require(inv.getItemCountById(cost.getKey()) >= cost.getValue());
                    gained += cost.getValue() * materials.get(cost.getKey());
                }
                ReliquarySystem.require(Double.isFinite(gained) && gained > 0);
                double total = st.progress + gained;
                int cap = c.value(OFFERING, 3);
                int count = (int) Math.floor(total / cap);
                ReliquarySystem.require(
                        count >= 0 && count <= c.value(OFFERING, 4) - st.extractedThisCycle);
                var payment =
                        costs.entrySet().stream()
                                .map(
                                        e ->
                                                new emu.grasscutter.data.common.ItemParamData(
                                                        e.getKey(), e.getValue()))
                                .toList();
                java.util.function.BooleanSupplier consume =
                        () -> {
                            if (!inv.payItems(payment)) return false;
                            for (var relic : relics) ReliquarySystem.require(inv.removeItem(relic));
                            return true;
                        };
                Runnable commit =
                        () -> {
                            st.progress = total - count * cap;
                            st.extractedThisCycle += count;
                            saveState(player);
                        };
                if (count == 0) {
                    ReliquarySystem.require(consume.getAsBoolean());
                    commit.run();
                } else
                    ReliquarySystem.require(
                            inv.addItems(
                                            List.of(new GameItem(c.value(PURCHASE, 3), count)),
                                            ActionReason.ReliquaryOffering,
                                            InventoryAddPolicy.ALL_OR_NOTHING,
                                            consume,
                                            commit)
                                    .allAccepted());
                rsp.addAllItemList(req.getItemListList())
                        .addAllReliquaryGuidList(req.getReliquaryGuidListList());
                sendDataNotify(player);
            }
        } catch (com.google.protobuf.InvalidProtocolBufferException
                | IllegalArgumentException
                | ArithmeticException e) {
            rsp.setRetcode(Retcode.RET_FAIL_VALUE);
        }
        send(player, PacketOpcodes.ReliquaryOfferExtractRsp, sequence, rsp.build());
    }

    public static void handleDefine(Player player, byte[] payload) {
        handleDefine(player, payload, 0);
    }

    public static void handleDefine(Player player, byte[] payload, int sequence) {
        var rsp = GetReliquaryPurchaseRsp.newBuilder();
        try {
            var req = GetReliquaryPurchaseReq.parseFrom(payload);
            rsp.setConfigId(req.getConfigId()).setScheduleId(req.getScheduleId());
            synchronized (player.getInventory()) {
                var c = ReliquaryConfig.get();
                var st = state(player);
                var inv = player.getInventory();
                var row = c.purchases.get(req.getConfigId());
                ReliquarySystem.require(
                        row != null
                                && req.getScheduleId() == st.scheduleId
                                && req.getReliquaryType() >= 1
                                && req.getReliquaryType() <= 5
                                && req.getAppendPropTypeListCount() == c.value(PURCHASE, 1));
                ReliquarySystem.require(
                        inv.getItemCountById(c.value(PURCHASE, 0)) > 0
                                && st.definedSuites.getOrDefault(row.offerSetId, 0)
                                        < c.value("CONST_VALUE_PURCHASE_RELIQUARY_PARAM", 0));
                var ids = row.items(req.getReliquaryType());
                ReliquarySystem.require(!ids.isEmpty() && ids.size() == row.weights.size());
                long sum = 0;
                for (int weight : row.weights) {
                    ReliquarySystem.require(weight >= 0);
                    sum = Math.addExact(sum, weight);
                }
                ReliquarySystem.require(sum > 0);
                long pick = ThreadLocalRandom.current().nextLong(sum);
                int selected = 0;
                for (int i = 0; i < ids.size(); i++) {
                    pick -= row.weights.get(i);
                    if (pick < 0) {
                        selected = ids.get(i);
                        break;
                    }
                }
                var itemData = GameData.getItemDataMap().get(selected);
                ReliquarySystem.require(
                        itemData != null
                                && itemData.getItemType() == ItemType.ITEM_RELIQUARY
                                && itemData.getSetId() == row.offerSetId
                                && itemData.getEquipType().getValue() == req.getReliquaryType());
                var relic =
                        createDefinedReliquary(
                                itemData,
                                req.getReliquaryType(),
                                req.getMainPropType(),
                                req.getAppendPropTypeListList());
                ReliquarySystem.require(relic != null);
                var costs = c.values(PURCHASE, 4);
                int cost = costs.get(req.getReliquaryType() - 1);
                ReliquarySystem.require(cost > 0);
                var material =
                        new emu.grasscutter.data.common.ItemParamData(c.value(PURCHASE, 3), cost);
                ReliquarySystem.require(
                        inv.addItems(
                                        List.of(relic),
                                        ActionReason.ReliquaryPurchase,
                                        InventoryAddPolicy.ALL_OR_NOTHING,
                                        () -> inv.payItems(List.of(material)),
                                        () -> {
                                            st.definedSuites.merge(
                                                    row.offerSetId, 1, Math::addExact);
                                            saveState(player);
                                        })
                                .allAccepted());
                sendDataNotify(player);
            }
        } catch (com.google.protobuf.InvalidProtocolBufferException
                | IllegalArgumentException
                | ArithmeticException e) {
            rsp.setRetcode(Retcode.RET_FAIL_VALUE);
        }
        send(player, PacketOpcodes.ReliquaryOfferDefineRsp, sequence, rsp.build());
    }

    public static void repairInventoryDefinedTemplates(Player player) {
        if (player == null || player.getInventory() == null) {
            return;
        }
        java.util.List<GameItem> changed = new ArrayList<>();
        for (GameItem item : player.getInventory()) {
            try {
                if (repairDefinedReliquaryTemplate(item)) {
                    item.save();
                    changed.add(item);
                }
            } catch (Throwable t) {
                Grasscutter.getLogger()
                        .warn(
                                "ArtifactTransmuter repair skip guid={}: {}",
                                item != null ? item.getGuid() : 0,
                                t.toString());
            }
        }
        if (!changed.isEmpty()) {
            player.sendPacket(
                    new emu.grasscutter.server.packet.send.PacketStoreItemChangeNotify(changed));
            Grasscutter.getLogger()
                    .debug(
                            "ArtifactTransmuter repaired-defined-templates uid={} count={}",
                            player.getUid(),
                            changed.size());
        }
    }

    private static GameItem createDefinedReliquary(
            ItemData piece, int equipType, int mainFightPropId, List<Integer> subFightPropIds) {
        if (piece == null) {
            return null;
        }

        GameItem item = new GameItem(piece);
        item.setLevel(1);
        item.setLocked(true);

        // Client ReliquaryOfferDefineReq main/sub fields are FightProperty ids
        // (see ReliquaryWear* / ReliquarySetScheme* excel), NOT main-depot list indices
        // and NOT affix groupId (EM is fightProp 28 but affix groupId 24).
        FightProperty wantMain = FightProperty.getPropById(mainFightPropId);
        int depotId = piece.getMainPropDepotId();
        ReliquaryMainPropData main = findMainProp(depotId, wantMain);
        if (main == null
                || subFightPropIds.size() != 2
                || new HashSet<>(subFightPropIds).size() != 2
                || subFightPropIds.contains(mainFightPropId)) return null;
        item.setMainPropId(main.getId());

        // Build append props using the STANDARD 5-star depot 501.
        // Newer set depots 961-965 in resources are "compressed" (one inflated value per group),
        // which makes +0 relics show multi-roll stats (e.g. CRIT 9.7%).
        List<Integer> append = new ArrayList<>();
        Set<FightProperty> used = new HashSet<>();
        ReliquaryMainPropData mainData =
                GameData.getReliquaryMainPropDataMap().get(item.getMainPropId());
        if (mainData != null && mainData.getFightProp() != null) {
            used.add(mainData.getFightProp());
        }
        int appendDepot = normalizeAppendDepot(piece.getAppendPropDepotId());
        // Official define: exactly 2 chosen substats + fill to 3 or 4 single rolls.
        List<Integer> chosen = new ArrayList<>();
        List<Integer> chosenAffixIds = new ArrayList<>();
        for (int fightPropId : subFightPropIds) {
            if (chosen.size() >= 2) {
                break;
            }
            ReliquaryAffixData affix = pickAffixByFightProp(appendDepot, fightPropId, used);
            if (affix != null) {
                append.add(affix.getId());
                used.add(affix.getFightProp());
                chosen.add(fightPropId);
                chosenAffixIds.add(affix.getId());
            } else {
                Grasscutter.getLogger()
                        .warn(
                                "ArtifactTransmuter define: no affix for fightProp={} depot={}",
                                fightPropId,
                                appendDepot);
            }
        }
        int target = Math.min(4, Math.max(3, effectiveAppendPropNum(piece)));
        while (append.size() < target) {
            ReliquaryAffixData affix = pickRandomAffix(appendDepot, used);
            if (affix == null) {
                break;
            }
            append.add(affix.getId());
            used.add(affix.getFightProp());
        }
        if (chosen.size() != 2 || append.size() != target) return null;
        item.getAppendPropIdList().clear();
        item.getAppendPropIdList().addAll(append);
        // Purple define icon plus at least 2 shared upgrade hits on the chosen lines, per the
        // official tutorial.
        item.markAsDefinedReliquary(chosenAffixIds);
        Grasscutter.getLogger()
                .debug(
                        "ArtifactTransmuter define stats item={} mainPropId={} mainFp={}"
                                + " chosenSubs={} append={} definite={} excelApn={} effApn={}",
                        item.getItemId(),
                        item.getMainPropId(),
                        mainData != null ? mainData.getFightProp() : wantMain,
                        chosen,
                        append,
                        chosenAffixIds,
                        piece.getAppendPropNum(),
                        effectiveAppendPropNum(piece));
        return item;
    }

    /**
     * Remap legacy defined relics stamped onto appendPropNum 0/1/2 templates, which have no reshape
     * tab) onto the preferred appendPropNum&gt;=3 classic piece for the same set/slot.
     *
     * @return true if itemId changed
     */
    public static boolean repairDefinedReliquaryTemplate(GameItem item) {
        if (item == null || item.getItemType() != ItemType.ITEM_RELIQUARY) {
            return false;
        }
        if (item.getPurchasedAppendPropIdList() == null
                || item.getPurchasedAppendPropIdList().isEmpty()) {
            return false;
        }
        ItemData cur = item.getItemData();
        if (cur == null) {
            return false;
        }
        // Remote ReliquaryExcel often stamps appendPropNum=4 on every template; use effective
        // count.
        if (effectiveAppendPropNum(cur) >= 3) {
            return false;
        }
        int equipType = cur.getEquipType() == null ? 0 : cur.getEquipType().getValue();
        ItemData better = pickDefinedPiece(cur.getSetId(), equipType);
        if (better == null || better.getId() == item.getItemId()) {
            return false;
        }
        if (effectiveAppendPropNum(better) < 3) {
            return false;
        }
        int oldId = item.getItemId();
        item.setItemId(better.getId());
        item.setItemData(better);
        Grasscutter.getLogger()
                .debug(
                        "ArtifactTransmuter repair-defined-template guid={} {} -> {} (effApn {} ->"
                                + " {}, excel {} -> {})",
                        item.getGuid(),
                        oldId,
                        better.getId(),
                        effectiveAppendPropNum(cur),
                        effectiveAppendPropNum(better),
                        cur.getAppendPropNum(),
                        better.getAppendPropNum());
        return true;
    }

    /**
     * Prefer classic 5-star templates with effective appendPropNum &gt;= 3 so the reshape UI stays
     * available after +20. Public for remapping already-crafted defined relics that used 0/1/2
     * templates.
     */
    public static ItemData pickDefinedPiece(int setId, int equipType) {
        ItemData best = null;
        int bestScore = Integer.MIN_VALUE;
        for (ItemData data : GameData.getItemDataMap().values()) {
            if (data == null || data.getItemType() != ItemType.ITEM_RELIQUARY) {
                continue;
            }
            if (data.getSetId() != setId || data.getRankLevel() != 5) {
                continue;
            }
            int et = data.getEquipType() == null ? 0 : data.getEquipType().getValue();
            if (et != equipType) {
                continue;
            }
            int score = scoreDefinedPiece(data);
            if (score > bestScore
                    || (score == bestScore && (best == null || data.getId() < best.getId()))) {
                best = data;
                bestScore = score;
            }
        }
        return best;
    }

    /**
     * Classic open-world 5-star families encode the initial affix count in the ones digit (71520..71524).
     * Some server resource dumps wrongly flatten excel {@code appendPropNum} to 4 for every row -
     * client still uses the real digit, so trust id%10 for classic 501+main depot pieces.
     */
    static int effectiveAppendPropNum(ItemData data) {
        if (data == null) {
            return 0;
        }
        int digit = Math.floorMod(data.getId(), 10);
        if (digit <= 4
                && data.getAppendPropDepotId() == 501
                && isClassicMainDepot(data.getMainPropDepotId())) {
            return digit;
        }
        return data.getAppendPropNum();
    }

    private static int scoreDefinedPiece(ItemData data) {
        int appendDepot = data.getAppendPropDepotId();
        int mainDepot = data.getMainPropDepotId();
        int score = 0;
        if (appendDepot == 501) {
            score += 100;
        } else if (appendDepot > 0 && appendDepot < 961) {
            score += 50;
        } else {
            // Compressed 961-965: soft-lock risk + wrong roll display.
            score -= 100;
        }
        if (isClassicMainDepot(mainDepot)) {
            score += 80;
        }
        int apn = effectiveAppendPropNum(data);
        if (apn >= 4) {
            score += 40;
        } else if (apn >= 3) {
            score += 30;
        } else {
            // The client ReliquaryDust gate uses the real template index; 0/1/2 hide reshaping.
            score -= 50;
        }
        return score;
    }

    private static boolean isClassicMainDepot(int depotId) {
        return depotId == 1000 || depotId == 2000 || depotId == 3000 || depotId == 4000 || depotId == 5000;
    }

    /** Classic 5-star main-prop depots by EquipType value. */
    private static int classicMainDepotForEquip(int equipType) {
        return switch (equipType) {
            case 1 -> 4000; // flower
            case 2 -> 2000; // plume
            case 3 -> 1000; // sands
            case 4 -> 5000; // goblet
            case 5 -> 3000; // circlet
            default -> 3000;
        };
    }

    private static ReliquaryMainPropData findMainProp(int depotId, FightProperty want) {
        if (want == null || want == FightProperty.FIGHT_PROP_NONE || depotId <= 0) {
            return null;
        }
        return GameData.getReliquaryMainPropDataMap().values().stream()
                .filter(m -> m.getPropDepotId() == depotId)
                .filter(m -> m.getWeight() > 0)
                .filter(m -> m.getFightProp() == want)
                .min(Comparator.comparingInt(ReliquaryMainPropData::getId))
                .orElse(null);
    }

    /** Map compressed 961-965 depots to classic tiered depot 501. */
    static int normalizeAppendDepot(int depotId) {
        if (depotId >= 961 && depotId <= 965) {
            return 501;
        }
        return depotId > 0 ? depotId : 501;
    }

    /**
     * Pick a +0 affix by FightProperty id from the client define UI. Matches
     * ReliquaryWearAppendPropExcel (2/3/5/6/8/9/20/22/28/23); EM is 28 here while
     * ReliquaryAffixExcel groupId for EM is 24. Tier (e.g. 2.7%/3.1%/3.5%/3.9% CRIT) is
     * weighted-random like normal relic rolls.
     */
    private static ReliquaryAffixData pickAffixByFightProp(
            int depotId, int fightPropId, Set<FightProperty> used) {
        FightProperty prop = FightProperty.getPropById(fightPropId);
        if (prop == FightProperty.FIGHT_PROP_NONE || used.contains(prop)) {
            return null;
        }
        List<ReliquaryAffixData> cands =
                GameData.getReliquaryAffixDataMap().values().stream()
                        .filter(a -> a.getDepotId() == depotId)
                        .filter(a -> a.getFightProp() == prop)
                        .filter(a -> a.getWeight() > 0)
                        .collect(Collectors.toList());
        if (cands.isEmpty() && depotId != 501) {
            cands =
                    GameData.getReliquaryAffixDataMap().values().stream()
                            .filter(a -> a.getDepotId() == 501)
                            .filter(a -> a.getFightProp() == prop)
                            .filter(a -> a.getWeight() > 0)
                            .collect(Collectors.toList());
        }
        return weightedPick(cands);
    }

    private static ReliquaryAffixData pickRandomAffix(int depotId, Set<FightProperty> used) {
        List<ReliquaryAffixData> cands =
                GameData.getReliquaryAffixDataMap().values().stream()
                        .filter(a -> a.getDepotId() == depotId)
                        .filter(a -> a.getWeight() > 0)
                        .filter(a -> !used.contains(a.getFightProp()))
                        .collect(Collectors.toList());
        if (cands.isEmpty() && depotId != 501) {
            return pickRandomAffix(501, used);
        }
        // Same as GameItem.addNewAppendProp: weight across all remaining affix rows (prop + tier).
        return weightedPick(cands);
    }

    private static ReliquaryAffixData weightedPick(List<ReliquaryAffixData> cands) {
        if (cands == null || cands.isEmpty()) {
            return null;
        }
        WeightedList<ReliquaryAffixData> randomList = new WeightedList<>();
        for (ReliquaryAffixData affix : cands) {
            randomList.add(affix.getWeight(), affix);
        }
        if (randomList.size() == 0) {
            return null;
        }
        return randomList.next();
    }

    @Entity(useDiscriminator = false)
    public static final class PlayerState extends ReliquaryState.Transmuter {}
}
