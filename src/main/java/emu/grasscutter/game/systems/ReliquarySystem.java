package emu.grasscutter.game.systems;

import com.google.protobuf.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.systems.ReliquaryState.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.BDIEPNMGNIBOuterClass.BDIEPNMGNIB;
import emu.grasscutter.net.proto.BMPEMFOICKAOuterClass.BMPEMFOICKA;
import emu.grasscutter.net.proto.DeleteReliquaryWearCustomDataReqOuterClass.DeleteReliquaryWearCustomDataReq;
import emu.grasscutter.net.proto.GKEDIBFPBOPOuterClass.GKEDIBFPBOP;
import emu.grasscutter.net.proto.GetGameplayRecommendationReqOuterClass.GetGameplayRecommendationReq;
import emu.grasscutter.net.proto.GetGameplayRecommendationRspOuterClass.GetGameplayRecommendationRsp;
import emu.grasscutter.net.proto.GetReliquaryDustReqOuterClass.GetReliquaryDustReq;
import emu.grasscutter.net.proto.GetReliquaryDustRspOuterClass.GetReliquaryDustRsp;
import emu.grasscutter.net.proto.GetReliquaryDustSelectReqOuterClass.GetReliquaryDustSelectReq;
import emu.grasscutter.net.proto.GetReliquaryDustSelectRspOuterClass.GetReliquaryDustSelectRsp;
import emu.grasscutter.net.proto.GetReliquaryLockGeneralOptionReqOuterClass.GetReliquaryLockGeneralOptionReq;
import emu.grasscutter.net.proto.GetReliquaryLockGeneralOptionRspOuterClass.GetReliquaryLockGeneralOptionRsp;
import emu.grasscutter.net.proto.GetReliquaryOfferingRspOuterClass.GetReliquaryOfferingRsp;
import emu.grasscutter.net.proto.GetReliquaryPurchaseRspOuterClass.GetReliquaryPurchaseRsp;
import emu.grasscutter.net.proto.GetReliquarySetSchemeReqOuterClass.GetReliquarySetSchemeReq;
import emu.grasscutter.net.proto.GetReliquarySetSchemeRspOuterClass.GetReliquarySetSchemeRsp;
import emu.grasscutter.net.proto.GetReliquaryTransferReqOuterClass.GetReliquaryTransferReq;
import emu.grasscutter.net.proto.GetReliquaryTransferRspOuterClass.GetReliquaryTransferRsp;
import emu.grasscutter.net.proto.GetReliquaryWearCustomDataReqOuterClass.GetReliquaryWearCustomDataReq;
import emu.grasscutter.net.proto.GetReliquaryWearCustomDataRspOuterClass.GetReliquaryWearCustomDataRsp;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.JAEFNNHIAPCOuterClass.JAEFNNHIAPC;
import emu.grasscutter.net.proto.JDPEKGBJFDAOuterClass.JDPEKGBJFDA;
import emu.grasscutter.net.proto.ONKDGOGEBIKOuterClass.ONKDGOGEBIK;
import emu.grasscutter.net.proto.PBPOECKENDBOuterClass.PBPOECKENDB;
import emu.grasscutter.net.proto.ReliquaryFilterStateNotifyOuterClass.ReliquaryFilterStateNotify;
import emu.grasscutter.net.proto.ReliquaryFilterStateSaveNotifyOuterClass.ReliquaryFilterStateSaveNotify;
import emu.grasscutter.net.proto.ReliquaryReforgeDataNotifyOuterClass.ReliquaryReforgeDataNotify;
import emu.grasscutter.net.proto.ReliquaryReforgeRecordOuterClass.ReliquaryReforgeRecord;
import emu.grasscutter.net.proto.ReliquarySetSchemeDataOuterClass.ReliquarySetSchemeData;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SaveReliquarySetSchemeReqOuterClass.SaveReliquarySetSchemeReq;
import emu.grasscutter.net.proto.SaveReliquarySetSchemeRspOuterClass.SaveReliquarySetSchemeRsp;
import emu.grasscutter.net.proto.SaveReliquaryWearCustomDataReqOuterClass.SaveReliquaryWearCustomDataReq;
import emu.grasscutter.net.proto.SaveReliquaryWearCustomDataRspOuterClass.SaveReliquaryWearCustomDataRsp;
import emu.grasscutter.net.proto.SetEquipAvatarPreviewReqOuterClass.SetEquipAvatarPreviewReq;
import emu.grasscutter.net.proto.SetEquipAvatarPreviewRspOuterClass.SetEquipAvatarPreviewRsp;
import emu.grasscutter.net.proto.SetEquipBatchReqOuterClass.SetEquipBatchReq;
import emu.grasscutter.net.proto.SetEquipBatchRspOuterClass.SetEquipBatchRsp;
import emu.grasscutter.net.proto.SetEquipLockStateBatchReqOuterClass.SetEquipLockStateBatchReq;
import emu.grasscutter.net.proto.SetEquipLockStateBatchRspOuterClass.SetEquipLockStateBatchRsp;
import emu.grasscutter.net.proto.SetReliquaryDefaultSchemeReqOuterClass.SetReliquaryDefaultSchemeReq;
import emu.grasscutter.net.proto.SetReliquaryLockGeneralOptionReqOuterClass.SetReliquaryLockGeneralOptionReq;
import emu.grasscutter.net.proto.Uint32PairOuterClass.Uint32Pair;
import emu.grasscutter.server.packet.send.*;

import java.util.*;

/** 圣遗物设置、搭配和分解共用一个处理入口。 */
public final class ReliquarySystem {
    public static final int[] REQUESTS = {
        PacketOpcodes.GetReliquaryTransferReq,
        PacketOpcodes.GetReliquaryLockGeneralOptionReq,
        PacketOpcodes.SetReliquaryLockGeneralOptionReq,
        PacketOpcodes.SetReliquaryDefaultSchemeReq,
        PacketOpcodes.SaveReliquarySetSchemeReq,
        PacketOpcodes.GetReliquarySetSchemeReq,
        PacketOpcodes.SetEquipLockStateBatchReq,
        PacketOpcodes.SetEquipBatchReq,
        PacketOpcodes.SetEquipAvatarPreviewReq,
        PacketOpcodes.GetReliquaryWearCustomDataReq,
        PacketOpcodes.SaveReliquaryWearCustomDataReq,
        PacketOpcodes.DeleteReliquaryWearCustomDataReq,
        PacketOpcodes.ReliquaryFilterStateSaveNotify,
        PacketOpcodes.ONKDGOGEBIK,
        PacketOpcodes.PBPOECKENDB,
        PacketOpcodes.JDPEKGBJFDA,
        PacketOpcodes.GetGameplayRecommendationReq,
        PacketOpcodes.GetReliquaryDustReq,
        PacketOpcodes.GetReliquaryDustSelectReq,
        PacketOpcodes.GetReliquaryPurchaseReq,
        PacketOpcodes.GetReliquaryOfferingReq
    };
    private final Player player;
    private final int sequence;

    public ReliquarySystem(Player player, int sequence) {
        this.player = Objects.requireNonNull(player);
        this.sequence = sequence;
    }

    static void require(boolean value) {
        if (!value) throw new IllegalArgumentException("圣遗物请求参数无效");
    }

    private ReliquaryState state() {
        return player.getReliquaryState();
    }

    private void send(int opcode, MessageLite message) {
        var packet = new BasePacket(opcode, sequence);
        packet.setData(message);
        player.sendPacket(packet);
    }

    private void save() {
        player.save();
    }

    public void handle(int opcode, byte[] data) throws InvalidProtocolBufferException {
        synchronized (player.getInventory()) {
            switch (opcode) {
                case PacketOpcodes.GetReliquaryPurchaseReq ->
                        ArtifactTransmuterSystem.handleDefine(player, data, sequence);
                case PacketOpcodes.GetReliquaryOfferingReq ->
                        ArtifactTransmuterSystem.handleExtract(player, data, sequence);
                case PacketOpcodes.GetReliquaryDustReq -> dust(GetReliquaryDustReq.parseFrom(data));
                case PacketOpcodes.GetReliquaryDustSelectReq ->
                        selectDust(GetReliquaryDustSelectReq.parseFrom(data));
                case PacketOpcodes.GetReliquaryTransferReq ->
                        transfer(GetReliquaryTransferReq.parseFrom(data));
                case PacketOpcodes.GetReliquaryLockGeneralOptionReq -> {
                    GetReliquaryLockGeneralOptionReq.parseFrom(data);
                    options();
                }
                case PacketOpcodes.SetReliquaryLockGeneralOptionReq ->
                        option(SetReliquaryLockGeneralOptionReq.parseFrom(data));
                case PacketOpcodes.SetReliquaryDefaultSchemeReq ->
                        defaultScheme(SetReliquaryDefaultSchemeReq.parseFrom(data));
                case PacketOpcodes.SaveReliquarySetSchemeReq ->
                        saveScheme(SaveReliquarySetSchemeReq.parseFrom(data));
                case PacketOpcodes.GetReliquarySetSchemeReq ->
                        schemes(GetReliquarySetSchemeReq.parseFrom(data));
                case PacketOpcodes.SetEquipLockStateBatchReq ->
                        lockBatch(SetEquipLockStateBatchReq.parseFrom(data));
                case PacketOpcodes.SetEquipBatchReq -> equipBatch(SetEquipBatchReq.parseFrom(data));
                case PacketOpcodes.SetEquipAvatarPreviewReq ->
                        preview(SetEquipAvatarPreviewReq.parseFrom(data));
                case PacketOpcodes.GetReliquaryWearCustomDataReq ->
                        wear(GetReliquaryWearCustomDataReq.parseFrom(data));
                case PacketOpcodes.SaveReliquaryWearCustomDataReq ->
                        saveWear(SaveReliquaryWearCustomDataReq.parseFrom(data));
                case PacketOpcodes.DeleteReliquaryWearCustomDataReq ->
                        deleteWear(DeleteReliquaryWearCustomDataReq.parseFrom(data));
                case PacketOpcodes.ReliquaryFilterStateSaveNotify ->
                        filter(ReliquaryFilterStateSaveNotify.parseFrom(data));
                case PacketOpcodes.ONKDGOGEBIK -> avatarFilters(ONKDGOGEBIK.parseFrom(data));
                case PacketOpcodes.PBPOECKENDB -> recommendation(PBPOECKENDB.parseFrom(data));
                case PacketOpcodes.JDPEKGBJFDA -> saveAvatarFilter(JDPEKGBJFDA.parseFrom(data));
                case PacketOpcodes.GetGameplayRecommendationReq ->
                        gameplay(GetGameplayRecommendationReq.parseFrom(data));
                default -> throw new IllegalArgumentException("未注册的圣遗物协议");
            }
        }
    }

    public void reject(int opcode) {
        Message.Builder builder =
                switch (opcode) {
                    case PacketOpcodes.GetReliquaryPurchaseReq ->
                            GetReliquaryPurchaseRsp.newBuilder();
                    case PacketOpcodes.GetReliquaryOfferingReq ->
                            GetReliquaryOfferingRsp.newBuilder();
                    case PacketOpcodes.GetReliquaryDustReq -> GetReliquaryDustRsp.newBuilder();
                    case PacketOpcodes.GetReliquaryDustSelectReq ->
                            GetReliquaryDustSelectRsp.newBuilder();
                    case PacketOpcodes.GetReliquaryTransferReq ->
                            GetReliquaryTransferRsp.newBuilder();
                    case PacketOpcodes.GetReliquaryLockGeneralOptionReq,
                            PacketOpcodes.SetReliquaryLockGeneralOptionReq,
                            PacketOpcodes.SetReliquaryDefaultSchemeReq ->
                            GetReliquaryLockGeneralOptionRsp.newBuilder();
                    case PacketOpcodes.SaveReliquarySetSchemeReq ->
                            SaveReliquarySetSchemeRsp.newBuilder();
                    case PacketOpcodes.GetReliquarySetSchemeReq ->
                            GetReliquarySetSchemeRsp.newBuilder();
                    case PacketOpcodes.SetEquipLockStateBatchReq ->
                            SetEquipLockStateBatchRsp.newBuilder();
                    case PacketOpcodes.SetEquipBatchReq -> SetEquipBatchRsp.newBuilder();
                    case PacketOpcodes.SetEquipAvatarPreviewReq ->
                            SetEquipAvatarPreviewRsp.newBuilder();
                    case PacketOpcodes.GetReliquaryWearCustomDataReq,
                            PacketOpcodes.DeleteReliquaryWearCustomDataReq ->
                            GetReliquaryWearCustomDataRsp.newBuilder();
                    case PacketOpcodes.SaveReliquaryWearCustomDataReq ->
                            SaveReliquaryWearCustomDataRsp.newBuilder();
                    case PacketOpcodes.ONKDGOGEBIK -> BMPEMFOICKA.newBuilder();
                    case PacketOpcodes.PBPOECKENDB -> JAEFNNHIAPC.newBuilder();
                    case PacketOpcodes.JDPEKGBJFDA -> BDIEPNMGNIB.newBuilder();
                    case PacketOpcodes.GetGameplayRecommendationReq ->
                            GetGameplayRecommendationRsp.newBuilder();
                    default -> null;
                };
        if (builder == null) return;
        var field = builder.getDescriptorForType().findFieldByName("retcode");
        if (field != null) builder.setField(field, Retcode.RET_FAIL_VALUE);
        int response =
                switch (opcode) {
                    case PacketOpcodes.GetReliquaryPurchaseReq ->
                            PacketOpcodes.GetReliquaryPurchaseRsp;
                    case PacketOpcodes.GetReliquaryOfferingReq ->
                            PacketOpcodes.GetReliquaryOfferingRsp;
                    case PacketOpcodes.GetReliquaryDustReq -> PacketOpcodes.GetReliquaryDustRsp;
                    case PacketOpcodes.GetReliquaryDustSelectReq ->
                            PacketOpcodes.GetReliquaryDustSelectRsp;
                    case PacketOpcodes.GetReliquaryTransferReq ->
                            PacketOpcodes.GetReliquaryTransferRsp;
                    case PacketOpcodes.GetReliquaryLockGeneralOptionReq,
                            PacketOpcodes.SetReliquaryLockGeneralOptionReq,
                            PacketOpcodes.SetReliquaryDefaultSchemeReq ->
                            PacketOpcodes.GetReliquaryLockGeneralOptionRsp;
                    case PacketOpcodes.SaveReliquarySetSchemeReq ->
                            PacketOpcodes.SaveReliquarySetSchemeRsp;
                    case PacketOpcodes.GetReliquarySetSchemeReq ->
                            PacketOpcodes.GetReliquarySetSchemeRsp;
                    case PacketOpcodes.SetEquipLockStateBatchReq ->
                            PacketOpcodes.SetEquipLockStateBatchRsp;
                    case PacketOpcodes.SetEquipBatchReq -> PacketOpcodes.SetEquipBatchRsp;
                    case PacketOpcodes.SetEquipAvatarPreviewReq ->
                            PacketOpcodes.SetEquipAvatarPreviewRsp;
                    case PacketOpcodes.GetReliquaryWearCustomDataReq,
                            PacketOpcodes.DeleteReliquaryWearCustomDataReq ->
                            PacketOpcodes.GetReliquaryWearCustomDataRsp;
                    case PacketOpcodes.SaveReliquaryWearCustomDataReq ->
                            PacketOpcodes.SaveReliquaryWearCustomDataRsp;
                    case PacketOpcodes.ONKDGOGEBIK -> PacketOpcodes.BMPEMFOICKA;
                    case PacketOpcodes.PBPOECKENDB -> PacketOpcodes.JAEFNNHIAPC;
                    case PacketOpcodes.JDPEKGBJFDA -> PacketOpcodes.BDIEPNMGNIB;
                    case PacketOpcodes.GetGameplayRecommendationReq ->
                            PacketOpcodes.GetGameplayRecommendationRsp;
                    default -> 0;
                };
        send(response, builder.build());
    }

    private GameItem item(long guid) {
        var item = player.getInventory().getItemByGuid(guid);
        require(guid != 0 && item != null && item.getItemData() != null);
        return item;
    }

    private GameItem relic(long guid) {
        var item = item(guid);
        require(item.getItemType() == ItemType.ITEM_RELIQUARY);
        return item;
    }

    public static boolean pending(Player player, GameItem item) {
        if (player == null || item == null || item.getItemType() != ItemType.ITEM_RELIQUARY)
            return false;
        var p = player.getReliquaryState().pending;
        return p != null
                && item.getObjectId() != null
                && item.getObjectId().toHexString().equals(p.itemKey);
    }

    private GameItem consumable(long guid) {
        var item = relic(guid);
        require(!item.isLocked() && item.getEquipCharacter() == 0 && !pending(player, item));
        return item;
    }

    private Avatar avatar(long guid) {
        var avatar = player.getAvatars().getAvatarByGuid(guid);
        require(avatar != null);
        return avatar;
    }

    private void transfer(GetReliquaryTransferReq req) {
        require(req.getGuidListCount() > 0 && req.getGuidListCount() <= 3000);
        var config = ReliquaryConfig.get();
        require(!config.transfer.isEmpty());
        var inputs = new LinkedHashMap<Long, GameItem>();
        long added = 0;
        for (long guid : req.getGuidListList()) {
            require(!inputs.containsKey(guid));
            var item = consumable(guid);
            require(item.getItemData().getBaseConvExp() > 0 && item.getTotalExp() >= 0);
            added =
                    Math.addExact(
                            added,
                            item.getItemData().getBaseConvExp()
                                    + (long) item.getTotalExp() * 4 / 5);
            require(added <= Integer.MAX_VALUE);
            inputs.put(guid, item);
        }
        int before = player.getProperty(PlayerProperty.PROP_RELIQUARY_TRANSFER_EXP);
        require(before >= 0);
        long remaining = before + added;
        var counts = new TreeMap<Integer, Integer>();
        for (var bundle : config.transfer) {
            long n = remaining / bundle.exp();
            remaining %= bundle.exp();
            for (var entry : bundle.items().entrySet())
                if (n > 0)
                    counts.merge(
                            entry.getKey(), Math.toIntExact(n * entry.getValue()), Math::addExact);
        }
        int balance = Math.toIntExact(remaining);
        var rewards =
                counts.entrySet().stream()
                        .map(e -> new GameItem(e.getKey(), e.getValue()))
                        .toList();
        Runnable commit =
                () ->
                        player.setProperty(
                                PlayerProperty.PROP_RELIQUARY_TRANSFER_EXP, balance, false);
        if (rewards.isEmpty()) {
            consume(inputs.values());
            commit.run();
        } else
            require(
                    player.getInventory()
                            .addItems(
                                    rewards,
                                    ActionReason.ReliquaryTransfer,
                                    InventoryAddPolicy.ALL_OR_NOTHING,
                                    () -> {
                                        consume(inputs.values());
                                        return true;
                                    },
                                    commit)
                            .allAccepted());
        player.sendPacket(
                new PacketPlayerPropNotify(player, PlayerProperty.PROP_RELIQUARY_TRANSFER_EXP));
        save();
        var rsp =
                GetReliquaryTransferRsp.newBuilder()
                        .setOldExp(before)
                        .setAddExp((int) added)
                        .setRemainExp(balance);
        counts.forEach(
                (id, count) ->
                        rsp.addTransferItemList(
                                ItemParam.newBuilder().setItemId(id).setCount(count)));
        send(PacketOpcodes.GetReliquaryTransferRsp, rsp.build());
    }

    private void consume(Collection<GameItem> inputs) {
        for (var value : inputs) require(player.getInventory().removeItem(value));
    }

    private void dust(GetReliquaryDustReq req) {
        var c = ReliquaryConfig.get();
        var value = relic(req.getGuid());
        var limits = c.values("CONST_VALUE_RELIQUARY_DUST_PARAM_1", 1);
        require(
                state().pending == null
                        && value.getLevel() == limits.get(0)
                        && value.getItemData().getRankLevel() == limits.get(1)
                        && ArtifactTransmuterSystem.effectiveAppendPropNum(value.getItemData())
                                >= limits.get(2));
        require(
                req.getAppendPropTypeListCount()
                        == c.value("CONST_VALUE_RELIQUARY_DUST_PARAM_1", 0));
        var chosen = req.getAppendPropTypeListList();
        require(new HashSet<>(chosen).size() == 2);
        int expected = ArtifactTransmuterSystem.effectiveAppendPropNum(value.getItemData());
        if (value.getItemData().getAddPropLevels() != null)
            for (int level : value.getItemData().getAddPropLevels())
                if (level <= value.getLevel()) expected++;
        require(value.getAppendPropIdList().size() == expected);
        for (int type : chosen) {
            var match =
                    value.getAppendPropIdList().stream()
                            .map(GameData.getReliquaryAffixDataMap()::get)
                            .filter(Objects::nonNull)
                            .filter(
                                    a ->
                                            a.getFightProp() != null
                                                    && a.getFightProp().getId() == type)
                            .findFirst();
            require(match.isPresent());
        }
        boolean purchased =
                value.getPurchasedAppendPropIdList() != null
                        && !value.getPurchasedAppendPropIdList().isEmpty();
        int cost =
                c.pairs("CONST_VALUE_RELIQUARY_DUST_PARAM_1", purchased ? 4 : 3)
                        .getOrDefault(value.getItemData().getEquipType().getValue(), 0);
        require(cost > 0);
        int progress = Math.addExact(state().dustProgress, cost), tier = 1;
        var tiers = c.pairs("CONST_VALUE_RELIQUARY_DUST_PARAM_2", 0);
        for (var threshold : tiers.entrySet())
            if (state().dustProgress < threshold.getKey() && progress >= threshold.getKey())
                tier = threshold.getValue();
        if (!tiers.isEmpty()) {
            int period = tiers.keySet().stream().max(Integer::compare).orElseThrow();
            if (progress >= period) progress %= period;
        }
        int guarantee = c.pairs("CONST_VALUE_RELIQUARY_DUST_PARAM_1", 5).getOrDefault(tier, 0);
        require(guarantee > 0);
        var candidate = ReliquaryDustSystem.reshuffleNative(value, chosen, guarantee);
        require(
                candidate != null
                        && candidate.size() == value.getAppendPropIdList().size()
                        && candidate.stream()
                                .allMatch(GameData.getReliquaryAffixDataMap()::containsKey));
        var pending = new Pending();
        pending.itemKey = value.ensurePersistenceId().toHexString();
        pending.oldAppend = new ArrayList<>(value.getAppendPropIdList());
        pending.newAppend = new ArrayList<>(candidate);
        pending.chosen = new ArrayList<>(chosen);
        require(
                player.getInventory()
                        .payItems(
                                List.of(
                                        new emu.grasscutter.data.common.ItemParamData(
                                                c.value("CONST_VALUE_RELIQUARY_DUST_PARAM_1", 2),
                                                cost))));
        state().pending = pending;
        state().dustProgress = progress;
        save();
        sendReforgeState();
        send(
                PacketOpcodes.GetReliquaryDustRsp,
                GetReliquaryDustRsp.newBuilder().setGuid(req.getGuid()).build());
    }

    private GameItem pendingItem() {
        var pending = state().pending;
        if (pending == null) return null;
        return player.getInventory().getItems().values().stream()
                .filter(
                        v ->
                                v.getObjectId() != null
                                        && v.getObjectId().toHexString().equals(pending.itemKey))
                .findFirst()
                .orElse(null);
    }

    public void sendReforgeState() {
        var rsp = ReliquaryReforgeDataNotify.newBuilder().setCurProgress(state().dustProgress);
        var item = pendingItem();
        if (item != null)
            rsp.addReforgeRecordList(
                    ReliquaryReforgeRecord.newBuilder()
                            .setGuid(item.getGuid())
                            .addAllAppendPropTypeList(state().pending.chosen)
                            .addAllAppendPropIdList(state().pending.newAppend));
        send(PacketOpcodes.ReliquaryReforgeDataNotify, rsp.build());
    }

    private void selectDust(GetReliquaryDustSelectReq req) {
        var value = pendingItem();
        require(
                value != null
                        && value.getGuid() == req.getGuid()
                        && value.getAppendPropIdList().equals(state().pending.oldAppend));
        if (req.getIsUseNew()) {
            value.getAppendPropIdList().clear();
            value.getAppendPropIdList().addAll(state().pending.newAppend);
            if (value.getDefiniteAppendPropIdList() != null)
                value.getDefiniteAppendPropIdList().clear();
            value.save();
            player.sendPacket(new PacketStoreItemChangeNotify(value));
            if (value.getEquipCharacter() != 0) {
                var avatar = player.getAvatars().getAvatarById(value.getEquipCharacter());
                if (avatar != null) avatar.recalcStats();
            }
        }
        state().pending = null;
        save();
        sendReforgeState();
        send(
                PacketOpcodes.GetReliquaryDustSelectRsp,
                GetReliquaryDustSelectRsp.getDefaultInstance());
    }

    private void options() {
        var rsp = GetReliquaryLockGeneralOptionRsp.newBuilder();
        state().enabledOptions.stream().sorted().forEach(rsp::addEnabledOptionIdList);
        state().disabledSets.stream().sorted().forEach(rsp::addDisabledReliquarySetIdList);
        send(PacketOpcodes.GetReliquaryLockGeneralOptionRsp, rsp.build());
    }

    private void option(SetReliquaryLockGeneralOptionReq req) {
        require(ReliquaryConfig.get().options.contains(req.getOptionId()));
        if (req.getIsOpen()) state().enabledOptions.add(req.getOptionId());
        else state().enabledOptions.remove(req.getOptionId());
        save();
        options(); // 独立应答号未确认，先同步已确认的设置快照。
    }

    private void defaultScheme(SetReliquaryDefaultSchemeReq req) {
        require(GameData.getReliquarySetDataMap().containsKey(req.getReliquarySetId()));
        if (req.getIsOpen()) state().disabledSets.remove(req.getReliquarySetId());
        else state().disabledSets.add(req.getReliquarySetId());
        save();
        options();
    }

    private void schemes(GetReliquarySetSchemeReq req) {
        require(req.getReliquarySetIdListCount() <= 1024);
        var rsp = GetReliquarySetSchemeRsp.newBuilder();
        for (int id : new LinkedHashSet<>(req.getReliquarySetIdListList())) {
            require(GameData.getReliquarySetDataMap().containsKey(id));
            var row = ReliquarySetSchemeData.newBuilder().setReliquarySetId(id);
            for (var scheme : ReliquaryConfig.get().defaults.getOrDefault(id, List.of()))
                row.addDefaultSchemeList(scheme.proto(true));
            for (var scheme : state().schemes.getOrDefault(id, List.of()))
                row.addCustomSchemeList(scheme.proto(false));
            rsp.addSetSchemeList(row);
        }
        send(PacketOpcodes.GetReliquarySetSchemeRsp, rsp.build());
    }

    private void saveScheme(SaveReliquarySetSchemeReq req) {
        var c = ReliquaryConfig.get();
        require(
                c.maxSchemes > 0
                        && GameData.getReliquarySetDataMap().containsKey(req.getReliquarySetId())
                        && req.hasScheme());
        var data = req.getScheme();
        require(data.getMainPropFilterListCount() <= 5 && data.getAppendPropFilterListCount() <= 5);
        var slots = new HashSet<Integer>();
        for (var row : data.getMainPropFilterListList()) {
            require(
                    slots.add(row.getEquipType())
                            && row.getEquipType() >= 1
                            && row.getEquipType() <= 5);
            validateProps(
                    row.getMainPropTypeListList(),
                    c.mainProps.getOrDefault(row.getEquipType(), Set.of()));
        }
        slots.clear();
        for (var row : data.getAppendPropFilterListList()) {
            require(
                    slots.add(row.getEquipType())
                            && row.getEquipType() >= 1
                            && row.getEquipType() <= 5
                            && row.getMinCount() >= 0
                            && row.getMinCount() <= 4);
            validateProps(row.getRequiredAppendPropListList(), c.appendProps);
            validateProps(row.getOptionalAppendPropListList(), c.appendProps);
        }
        var list = state().schemes.computeIfAbsent(req.getReliquarySetId(), k -> new ArrayList<>());
        int index = -1;
        for (int i = 0; i < list.size(); i++)
            if (list.get(i).schemeId == data.getSchemeId()) {
                index = i;
                break;
            }
        var copy = LockScheme.from(data);
        if (index < 0) {
            require(list.size() < c.maxSchemes);
            list.add(copy);
        } else list.set(index, copy);
        save();
        send(
                PacketOpcodes.SaveReliquarySetSchemeRsp,
                SaveReliquarySetSchemeRsp.getDefaultInstance());
    }

    private static void validateProps(List<Integer> values, Set<Integer> allowed) {
        require(
                values.size() <= allowed.size()
                        && new HashSet<>(values).size() == values.size()
                        && allowed.containsAll(values));
    }

    private void lockBatch(SetEquipLockStateBatchReq req) {
        require(req.getEquipGuidListCount() > 0 && req.getEquipGuidListCount() <= 6000);
        var items = new LinkedHashMap<Long, GameItem>();
        for (long guid : req.getEquipGuidListList()) {
            var item = item(guid);
            require(
                    item.getItemType() == ItemType.ITEM_WEAPON
                            || item.getItemType() == ItemType.ITEM_RELIQUARY);
            items.put(guid, item);
        }
        for (var item : items.values()) {
            item.setLocked(req.getIsLocked());
            item.save();
        }
        player.sendPacket(new PacketStoreItemChangeNotify(new ArrayList<>(items.values())));
        send(
                PacketOpcodes.SetEquipLockStateBatchRsp,
                SetEquipLockStateBatchRsp.newBuilder().setIsLocked(req.getIsLocked()).build());
    }

    private Map<Integer, GameItem> equipment(Avatar avatar, List<Long> guids) {
        require(guids.size() <= 6);
        var result = new LinkedHashMap<Integer, GameItem>();
        var seen = new HashSet<Long>();
        for (int i = 0; i < guids.size(); i++) {
            long guid = guids.get(i);
            if (guid == 0) {
                require(i == 0 && avatar.getEquipBySlot(EquipType.EQUIP_WEAPON) == null);
                continue;
            }
            require(seen.add(guid));
            var item = item(guid);
            require(item.getItemData().getEquipType() != null);
            int slot = item.getItemData().getEquipType().getValue();
            require(slot >= 1 && slot <= 6 && !result.containsKey(slot));
            if (slot == 6) require(item == avatar.getEquipBySlot(EquipType.EQUIP_WEAPON));
            else require(item.getItemType() == ItemType.ITEM_RELIQUARY);
            result.put(slot, item);
        }
        return result;
    }

    private void equipBatch(SetEquipBatchReq req) {
        require(
                req.getEquipGuidListCount() > 0
                        && req.getEquipTypeValue() >= 0
                        && req.getEquipTypeValue() <= 2);
        var avatar = avatar(req.getAvatarGuid());
        var equips = equipment(avatar, req.getEquipGuidListList());
        for (var entry : equips.entrySet())
            if (entry.getKey() != 6)
                require(
                        player.getInventory()
                                .equipItem(req.getAvatarGuid(), entry.getValue().getGuid()));
        send(PacketOpcodes.SetEquipBatchRsp, SetEquipBatchRsp.getDefaultInstance());
    }

    private void preview(SetEquipAvatarPreviewReq req) {
        require(req.getIndex() >= 0 && req.getIndex() <= 2);
        var avatar = avatar(req.getAvatarGuid());
        var equips = equipment(avatar, req.getEquipGuidListList());
        var before = avatar.previewEquipment(Map.of());
        var after = avatar.previewEquipment(equips);
        send(
                PacketOpcodes.SetEquipAvatarPreviewRsp,
                SetEquipAvatarPreviewRsp.newBuilder()
                        .setIndex(req.getIndex())
                        .setAvatarInfo(after)
                        .setPreviewAvatarInfo(before)
                        .build());
    }

    private static String key(int avatar, int element, int index) {
        return ReliquaryState.wearKey(avatar, element, index);
    }

    private void wear(GetReliquaryWearCustomDataReq req) {
        wear(req.getAvatarId(), req.getAvatarElementType());
    }

    private void wear(int avatar, int element) {
        var rsp = GetReliquaryWearCustomDataRsp.newBuilder();
        state().wear.values().stream()
                .filter(v -> v.avatarId == avatar && v.element == element)
                .sorted(Comparator.comparingInt(v -> v.index))
                .forEach(v -> rsp.addCustomDataList(v.proto()));
        send(PacketOpcodes.GetReliquaryWearCustomDataRsp, rsp.build());
    }

    private void saveWear(SaveReliquaryWearCustomDataReq req) {
        require(req.hasCustomData());
        var v = req.getCustomData();
        require(
                player.getAvatars().getAvatarById(v.getAvatarId()) != null
                        && v.getAvatarElementType() >= 0
                        && v.getAvatarElementType() <= 8
                        && v.getIndex() >= 0
                        && v.getIndex() <= 1
                        && v.getReliquarySetIdListCount() <= 16
                        && v.getMainPropTypeMapCount() <= 3);
        for (int id : v.getReliquarySetIdListList())
            require(GameData.getReliquarySetDataMap().containsKey(id));
        var slots = new HashSet<Integer>();
        var allowed = ReliquaryConfig.get();
        for (var pair : v.getMainPropTypeMapList())
            require(
                    pair.getKey() >= 3
                            && pair.getKey() <= 5
                            && slots.add(pair.getKey())
                            && allowed.mainProps
                                    .getOrDefault(pair.getKey(), Set.of())
                                    .contains(pair.getValue()));
        var all = new HashSet<Integer>();
        allowed.mainProps.values().forEach(all::addAll);
        validateProps(v.getMainPropTypeListList(), all);
        validateProps(v.getAppendPropTypeListList(), allowed.appendProps);
        state().wear
                .put(key(v.getAvatarId(), v.getAvatarElementType(), v.getIndex()), Wear.from(v));
        save();
        send(
                PacketOpcodes.SaveReliquaryWearCustomDataRsp,
                SaveReliquaryWearCustomDataRsp.getDefaultInstance());
    }

    private void deleteWear(DeleteReliquaryWearCustomDataReq req) {
        require(req.getIndex() >= 0 && req.getIndex() <= 1);
        state().deleteWear(req.getAvatarId(), req.getAvatarElementType(), req.getIndex());
        save();
        wear(req.getAvatarId(), req.getAvatarElementType());
    }

    private void filter(ReliquaryFilterStateSaveNotify req) {
        require(req.getReliquaryFilterStateMapCount() <= 4096);
        state().replaceFilters(req.getReliquaryFilterStateMapList());
        save();
        sendFilter();
    }

    public void sendFilter() {
        var rsp = ReliquaryFilterStateNotify.newBuilder();
        state().filters
                .forEach(
                        (key, list) ->
                                list.forEach(
                                        value ->
                                                rsp.addReliquaryFilterStateMap(
                                                        Uint32Pair.newBuilder()
                                                                .setKey(key)
                                                                .setValue(value))));
        send(PacketOpcodes.ReliquaryFilterStateNotify, rsp.build());
    }

    private void recommendation(PBPOECKENDB req) {
        send(
                PacketOpcodes.JAEFNNHIAPC,
                JAEFNNHIAPC
                        .newBuilder()
                        .setAvatarId(req.getAvatarId())
                        .setElementType(req.getElementType())
                        .build());
    }

    private void avatarFilters(ONKDGOGEBIK req) {
        require(req.getAvatarIdListCount() <= 1024);
        var rsp = BMPEMFOICKA.newBuilder();
        for (int id : new LinkedHashSet<>(req.getAvatarIdListList())) {
            require(player.getAvatars().getAvatarById(id) != null);
            var stored = state().avatarFilters.get(id);
            rsp.addParamList(
                    stored == null
                            ? GKEDIBFPBOP.newBuilder().setAvatarId(id).build()
                            : stored.proto());
        }
        send(PacketOpcodes.BMPEMFOICKA, rsp.build());
    }

    private void saveAvatarFilter(JDPEKGBJFDA req) {
        require(req.hasODHLHLFNLGD());
        var data = req.getODHLHLFNLGD();
        require(
                player.getAvatars().getAvatarById(data.getAvatarId()) != null
                        && data.getBJNFCNLFMFDCount() <= 1024
                        && data.getKAGPLLAFPIECount() <= 5);
        for (int id : data.getBJNFCNLFMFDList())
            require(GameData.getReliquarySetDataMap().containsKey(id));
        var copy = new AvatarFilter();
        copy.avatarId = data.getAvatarId();
        copy.sets = new ArrayList<>(data.getBJNFCNLFMFDList());
        var slots = new HashSet<Integer>();
        for (var slot : data.getKAGPLLAFPIEList()) {
            require(
                    slot.getEquipType() >= 1
                            && slot.getEquipType() <= 5
                            && slots.add(slot.getEquipType()));
            require(slot.getAffixListCount() <= 64);
            var row = new FilterSlot();
            row.equipType = slot.getEquipType();
            row.INLHDNOJOMN = slot.getINLHDNOJOMN();
            row.affixes = new ArrayList<>(slot.getAffixListList());
            copy.slots.add(row);
        }
        if (copy.sets.isEmpty() && copy.slots.isEmpty())
            state().avatarFilters.remove(copy.avatarId);
        else state().avatarFilters.put(copy.avatarId, copy);
        save();
        send(PacketOpcodes.BDIEPNMGNIB, BDIEPNMGNIB.getDefaultInstance());
    }

    private void gameplay(GetGameplayRecommendationReq req) {
        var rsp =
                GetGameplayRecommendationRsp.newBuilder()
                        .setAvatarId(req.getAvatarId())
                        .setType(req.getType());
        boolean matched = false;
        for (var entry : req.getAllFields().entrySet()) {
            if (entry.getKey().getContainingOneof() == null
                    || !(entry.getValue() instanceof Message request)) continue;
            var field =
                    rsp.getDescriptorForType()
                            .findFieldByName(
                                    entry.getKey().getName().replace("Request", "Response"));
            if (field == null) continue;
            var child = rsp.newBuilderForField(field);
            for (var value : request.getAllFields().entrySet()) {
                var target = child.getDescriptorForType().findFieldByName(value.getKey().getName());
                if (target != null
                        && target.getType() == value.getKey().getType()
                        && target.isRepeated() == value.getKey().isRepeated()
                        && target.getJavaType()
                                != com.google.protobuf.Descriptors.FieldDescriptor.JavaType.MESSAGE
                        && target.getJavaType()
                                != com.google.protobuf.Descriptors.FieldDescriptor.JavaType.ENUM)
                    child.setField(target, value.getValue());
            }
            rsp.setField(field, child.build());
            matched = true;
        }
        if (!matched) rsp.setRetcode(Retcode.RET_FAIL_VALUE);
        send(PacketOpcodes.GetGameplayRecommendationRsp, rsp.build());
    }
}
