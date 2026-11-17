package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetReliquaryOfferingRspOuterClass.GetReliquaryOfferingRsp;

public class PacketReliquaryOfferExtractRsp extends BasePacket {
    public PacketReliquaryOfferExtractRsp() {
        super(PacketOpcodes.ReliquaryOfferExtractRsp);
        setData(GetReliquaryOfferingRsp.getDefaultInstance());
    }
}
