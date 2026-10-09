package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.NpcTalkReqOuterClass.NpcTalkReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketNpcTalkRsp;

@Opcodes(PacketOpcodes.NpcTalkReq)
public class HandlerNpcTalkReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = NpcTalkReq.parseFrom(payload);
        int talkId = req.getTalkId();
        boolean tracePrologue = talkId == 35404 || talkId == 35601 || talkId == 35604 || talkId == 36001;

        if (talkId == 35601) {
            session.beginAmberInputTrace();
        }
        if (tracePrologue) {
            Grasscutter.getLogger().info(
                    "[Prologue] NpcTalkReq uid={} talk={} npcEntity={} entity={}",
                    session.getPlayer().getUid(), talkId, req.getNpcEntityId(), req.getEntityId());
        }

        try {
            // A valid talk request must receive a response even when a quest/talk action fails.
            // This does not mark a failed quest complete or bypass a cutscene.
            runTalkAndReply(
                    () -> session.getPlayer().getTalkManager().triggerTalkAction(talkId, req.getEntityId()),
                    () -> {
                        session.send(new PacketNpcTalkRsp(req.getNpcEntityId(), talkId, req.getEntityId()));
                        if (tracePrologue) {
                            Grasscutter.getLogger().info(
                                    "[Prologue] NpcTalkRsp sent uid={} talk={}",
                                    session.getPlayer().getUid(), talkId);
                        }
                    });
        } catch (RuntimeException e) {
            Grasscutter.getLogger().error("NpcTalkReq failed for talk {}", talkId, e);
        }
    }

    static void runTalkAndReply(Runnable talkAction, Runnable sendReply) {
        try {
            talkAction.run();
        } finally {
            sendReply.run();
        }
    }
}
