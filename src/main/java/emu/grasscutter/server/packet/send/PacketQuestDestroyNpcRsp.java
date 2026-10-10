package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.QuestDestroyNpcRspOuterClass.QuestDestroyNpcRsp;

public class PacketQuestDestroyNpcRsp extends BasePacket {

    public PacketQuestDestroyNpcRsp(int npcId, int parentQuestId, int retCode) {
        this(npcId, parentQuestId, retCode, 0);
    }

    public PacketQuestDestroyNpcRsp(int npcId, int parentQuestId, int retCode, int requestSequence) {
        super(PacketOpcodes.QuestDestroyNpcRsp, requestSequence);

        QuestDestroyNpcRsp proto =
                QuestDestroyNpcRsp.newBuilder()
                        .setNpcId(npcId)
                        .setParentQuestId(parentQuestId)
                        .setRetcode(retCode)
                        .build();

        this.setData(proto);
    }
}
