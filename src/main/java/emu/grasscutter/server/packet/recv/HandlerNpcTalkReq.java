package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.managers.StatueUnlockQuestBridge;
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

        // Quest 303 drives Statue-of-the-Seven activation. Compatibility code may have exposed the
        // client Talk gate as FINISHED; restore the locked statue's real quest immediately before
        // the Talk event so GameQuest.finish() can execute the official unlock point/area actions.
        StatueUnlockQuestBridge.prepareForTalk(player, req.getTalkId());

        int npcEntityId = req.getNpcEntityId() != 0 ? req.getNpcEntityId() : req.getEntityId();
        if (req.getTalkId() == 35216) {
            emu.grasscutter.Grasscutter.getLogger()
                    .info(
                            "[quest352] talk-recv uid={} talk={} npcEntity={} entity={}",
                            player.getUid(), req.getTalkId(),
                            req.getNpcEntityId(), req.getEntityId());
        }
        player.getTalkManager().triggerTalkAction(req.getTalkId(), npcEntityId);
        session.send(new PacketNpcTalkRsp(req.getNpcEntityId(), req.getTalkId(), req.getEntityId()));
    }
}
