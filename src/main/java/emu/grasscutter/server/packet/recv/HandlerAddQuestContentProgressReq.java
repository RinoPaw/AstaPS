package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AddQuestContentProgressReqOuterClass.AddQuestContentProgressReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAddQuestContentProgressRsp;

@Opcodes(PacketOpcodes.AddQuestContentProgressReq)
public class HandlerAddQuestContentProgressReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = AddQuestContentProgressReq.parseFrom(payload);
        var questManager = session.getPlayer().getQuestManager();

        // Find all conditions in quest that are the same as the given one
        var type = QuestContent.getContentTriggerByValue(req.getContentType());
        if (type != null) {
            questManager.queueEvent(type, req.getParam());
        }

        session.send(new PacketAddQuestContentProgressRsp(req.getContentType()));
    }
}
