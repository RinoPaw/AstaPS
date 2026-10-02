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
        var player = session.getPlayer();
        var questManager = player.getQuestManager();

        // Find all conditions in quest that are the same as the given one
        var type = QuestContent.getContentTriggerByValue(req.getContentType());
        var quest35104 = questManager.getQuestById(35104);
        emu.grasscutter.Grasscutter.getLogger()
                .info(
                        "[quest351] progress-single uid={} typeRaw={} type={} param={} addProgress={} quest35104={}",
                        player.getUid(),
                        req.getContentType(),
                        type,
                        req.getParam(),
                        req.getAddProgress(),
                        quest35104 != null ? quest35104.getState() : null);
        if (type != null) {
            questManager.queueEvent(type, req.getParam());
        }

        session.send(new PacketAddQuestContentProgressRsp(req.getContentType()));
    }
}
