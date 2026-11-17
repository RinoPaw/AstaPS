package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetReliquaryPurchaseRspOuterClass.GetReliquaryPurchaseRsp;

public class PacketReliquaryOfferDefineRsp extends BasePacket {
    public PacketReliquaryOfferDefineRsp(int configId) {
        super(PacketOpcodes.ReliquaryOfferDefineRsp);
        setData(GetReliquaryPurchaseRsp.newBuilder().setConfigId(configId));
    }
}
