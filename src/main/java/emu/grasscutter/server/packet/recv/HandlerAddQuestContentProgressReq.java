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
        if (req.getParam() >= 35100 && req.getParam() <= 35205) {
            emu.grasscutter.Grasscutter.getLogger()
                    .info(
                            "[quest-progress-c2s] uid={} source=single type={} contentType={} param={} addProgress={}",
                            session.getPlayer().getUid(),
                            type,
                            req.getContentType(),
                            req.getParam(),
                            req.getAddProgress());
        }
        if (type != null) {
            questManager.queueEvent(type, req.getParam());
        }

        session.send(new PacketAddQuestContentProgressRsp(req.getContentType()));
    }
}
