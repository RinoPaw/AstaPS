package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.managers.StatueTalkQuests;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.NpcTalkReqOuterClass.NpcTalkReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketNpcTalkRsp;

@Opcodes(PacketOpcodes.NpcTalkReq)
public class HandlerNpcTalkReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = NpcTalkReq.parseFrom(payload);
        var player = session.getPlayer();
        int talkId = req.getTalkId();
        boolean statueTalk = StatueTalkQuests.all().containsValue(talkId);

        if (statueTalk) {
            var quest = player.getQuestManager().getQuestById(talkId);
            Grasscutter.getLogger()
                    .info(
                            "[statue-probe] NpcTalkReq uid={} talk={} npcEntity={} entity={} beforeState={}",
                            player.getUid(),
                            talkId,
                            req.getNpcEntityId(),
                            req.getEntityId(),
                            quest == null ? null : quest.getState());
        }

        player.getTalkManager().triggerTalkAction(talkId, req.getEntityId());

        if (statueTalk) {
            var quest = player.getQuestManager().getQuestById(talkId);
            Grasscutter.getLogger()
                    .info(
                            "[statue-probe] NpcTalkReq complete uid={} talk={} afterState={}",
                            player.getUid(),
                            talkId,
                            quest == null ? null : quest.getState());
        }

        session.send(new PacketNpcTalkRsp(req.getNpcEntityId(), talkId, req.getEntityId()));
    }
}
