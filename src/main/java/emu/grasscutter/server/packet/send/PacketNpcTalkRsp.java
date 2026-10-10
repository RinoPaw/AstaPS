package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.NpcTalkRspOuterClass.NpcTalkRsp;

public class PacketNpcTalkRsp extends BasePacket {
    public PacketNpcTalkRsp(int npcEntityId, int curTalkId, int entityId) {
        this(npcEntityId, curTalkId, entityId, 0);
    }

    public PacketNpcTalkRsp(int npcEntityId, int curTalkId, int entityId, int requestSequence) {
        super(PacketOpcodes.NpcTalkRsp, requestSequence);

        NpcTalkRsp p =
                NpcTalkRsp.newBuilder()
                        .setNpcEntityId(npcEntityId)
                        .setCurTalkId(curTalkId)
                        .setEntityId(entityId)
                        .build();

        this.setData(p);
    }
}
