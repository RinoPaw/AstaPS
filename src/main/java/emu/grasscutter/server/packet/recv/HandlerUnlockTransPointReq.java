package emu.grasscutter.server.packet.recv;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.TransPointUnlockHelper;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass;
import emu.grasscutter.net.proto.UnlockTransPointReqOuterClass.UnlockTransPointReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketUnlockTransPointRsp;

@Opcodes(PacketOpcodes.UnlockTransPointReq)
public class HandlerUnlockTransPointReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        UnlockTransPointReq req = UnlockTransPointReq.parseFrom(payload);
        var entry = GameData.getScenePointEntryById(req.getSceneId(), req.getPointId());
        boolean isStatue =
                emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(
                        entry != null ? entry.getPointData() : null);
        boolean unlocked =
                TransPointUnlockHelper.unlock(
                        session.getPlayer(), req.getSceneId(), req.getPointId(), isStatue);
        emu.grasscutter.Grasscutter.getLogger()
                .info(
                        "UnlockTransPointReq uid={} scene={} point={} statue={} unlocked={}",
                        session.getPlayer().getUid(),
                        req.getSceneId(),
                        req.getPointId(),
                        isStatue,
                        unlocked);
        // UnlockTransPointRsp has no known 7.1 CmdId, so the client never hears back and leaves the
        // point drawn as locked. Resend the scene's point list, as the statue auto-unlock does.
        if (unlocked) {
            session.getPlayer()
                    .sendPacket(
                            new emu.grasscutter.server.packet.send.PacketGetScenePointRsp(
                                    session.getPlayer(), req.getSceneId()));
        }
        session
                .getPlayer()
                .sendPacket(
                        new PacketUnlockTransPointRsp(
                                unlocked
                                        ? RetcodeOuterClass.Retcode.RET_SUCC
                                        : RetcodeOuterClass.Retcode.RET_FAIL));
    }
}
