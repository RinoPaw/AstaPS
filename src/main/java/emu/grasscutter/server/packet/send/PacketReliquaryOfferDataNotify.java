package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.systems.ReliquaryState;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetReliquaryOfferPurchaseInfoNotifyOuterClass.GetReliquaryOfferPurchaseInfoNotify;
import emu.grasscutter.net.proto.ReliquaryPurchaseRecordOuterClass.ReliquaryPurchaseRecord;

/** 档期、进度和结束时间分别使用 7.2 的真实字段。 */
public class PacketReliquaryOfferDataNotify extends BasePacket {
    public PacketReliquaryOfferDataNotify(ReliquaryState.Transmuter state) {
        super(PacketOpcodes.ReliquaryOfferDataNotify);
        state.ensureCycle(System.currentTimeMillis() / 1000);
        int whole = (int) Math.floor(state.progress);
        var builder =
                GetReliquaryOfferPurchaseInfoNotify.newBuilder()
                        .setOfferingProgress(whole)
                        .setOfferingProgressExtra((float) (state.progress - whole))
                        .setOfferingCount(state.extractedThisCycle)
                        .setScheduleId(state.scheduleId)
                        .setEndTime(state.cycleEndUnix);
        state.definedSuites.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(
                        e ->
                                builder.addPurchaseRecordList(
                                        ReliquaryPurchaseRecord.newBuilder()
                                                .setReliquarySetId(e.getKey())
                                                .setPurchaseCount(e.getValue())));
        setData(builder);
    }

    public PacketReliquaryOfferDataNotify(ReliquaryState.Transmuter state, boolean ignored) {
        this(state);
    }
}
