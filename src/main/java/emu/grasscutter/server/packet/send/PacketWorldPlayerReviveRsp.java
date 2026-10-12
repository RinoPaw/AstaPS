package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.WorldPlayerReviveRspOuterClass.WorldPlayerReviveRsp;

public class PacketWorldPlayerReviveRsp extends BasePacket {
    // Exact 7.1 Global static recovery leaves CmdId 7003 as the sole field-14 S2C candidate.
    // Keep the candidate local until a live 5232 -> 7003 revive transaction confirms promotion.
    private static final int OPCODE_71_CANDIDATE = 7003;

    public PacketWorldPlayerReviveRsp() {
        super(OPCODE_71_CANDIDATE);

        WorldPlayerReviveRsp.Builder proto = WorldPlayerReviveRsp.newBuilder();

        this.setData(proto.build());
    }
}
