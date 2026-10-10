package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.proto.QuestDestroyNpcReqOuterClass.QuestDestroyNpcReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketQuestDestroyNpcRsp;
import lombok.val;

@Opcodes(PacketOpcodes.QuestDestroyNpcReq)
public class HandlerQuestDestroyNpcReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        val req = QuestDestroyNpcReq.parseFrom(payload);
        int sequence = header != null && header.length > 0
                ? PacketHead.parseFrom(header).getClientSequenceId() : 0;
        Grasscutter.getLogger().info(
                "[AmberWire] QuestDestroyNpcReq uid={} seq={} npcId={} parentQuestId={}",
                session.getPlayer() != null ? session.getPlayer().getUid() : 0,
                sequence, req.getNpcId(), req.getParentQuestId());
        session.send(new PacketQuestDestroyNpcRsp(
                req.getNpcId(), req.getParentQuestId(), 0, sequence));
    }
}
