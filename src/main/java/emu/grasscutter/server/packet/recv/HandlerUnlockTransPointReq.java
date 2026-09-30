package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketUnlockTransPointRsp;

@Opcodes(PacketOpcodes.UnlockTransPointReq)
public class HandlerUnlockTransPointReq extends PacketHandler {
    /** Live OSREL 7.1 wire opcode, confirmed from a waypoint unlock request. */
    public static final int OPCODE_7_1 = 9369;

    // Live 7.1 payload 08 06 60 03 => point_id=6 (field 1), scene_id=3 (field 12).
    // The checked-in generated UnlockTransPointReq still has stale field numbers.
    private static final int POINT_ID_FIELD_7_1 = 1;
    private static final int SCENE_ID_FIELD_7_1 = 12;

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int pointId = 0;
        int sceneId = 0;

        var input = CodedInputStream.newInstance(payload);
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) {
                break;
            }

            int field = WireFormat.getTagFieldNumber(tag);
            if (WireFormat.getTagWireType(tag) != WireFormat.WIRETYPE_VARINT) {
                input.skipField(tag);
                continue;
            }

            if (field == POINT_ID_FIELD_7_1) {
                pointId = input.readUInt32();
            } else if (field == SCENE_ID_FIELD_7_1) {
                sceneId = input.readUInt32();
            } else {
                input.skipField(tag);
            }
        }

        var entry = GameData.getScenePointEntryById(sceneId, pointId);
        if (sceneId <= 0 || pointId <= 0 || entry == null) {
            Grasscutter.getLogger()
                    .warn(
                            "Invalid 7.1 UnlockTransPointReq uid={} scene={} point={} payloadLen={}",
                            session.getPlayer() != null ? session.getPlayer().getUid() : 0,
                            sceneId,
                            pointId,
                            payload == null ? 0 : payload.length);
            sendResult(session, false);
            return;
        }

        boolean isStatue =
                emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(entry.getPointData());
        boolean unlocked =
                session.getPlayer().getProgressManager().unlockTransPoint(sceneId, pointId, isStatue);

        Grasscutter.getLogger()
                .info(
                        "UnlockTransPointReq 7.1 uid={} scene={} point={} statue={} unlocked={}",
                        session.getPlayer().getUid(),
                        sceneId,
                        pointId,
                        isStatue,
                        unlocked);
        sendResult(session, unlocked);
    }

    private static void sendResult(GameSession session, boolean success) {
        // UnlockTransPointRsp is still unresolved for 7.1. Never emit opcode 0; the actual unlock
        // already sends ScenePointUnlockNotify from PlayerProgressManager.
        if (PacketOpcodes.UnlockTransPointRsp <= 0) {
            return;
        }

        session.getPlayer()
                .sendPacket(
                        new PacketUnlockTransPointRsp(
                                success
                                        ? RetcodeOuterClass.Retcode.RET_SUCC
                                        : RetcodeOuterClass.Retcode.RET_FAIL));
    }
}
