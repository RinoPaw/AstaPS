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
        var player = session.getPlayer();
        var entry = GameData.getScenePointEntryById(req.getSceneId(), req.getPointId());
        boolean isStatue =
                emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(
                        entry != null ? entry.getPointData() : null);
        boolean previouslyUnlocked =
                player.getUnlockedScenePoints(req.getSceneId()).contains(req.getPointId());
        boolean forceLocked = player.isScenePointForceLocked(req.getSceneId(), req.getPointId());
        var playerPos = player.getPosition();
        var pointPos =
                entry != null && entry.getPointData() != null
                        ? entry.getPointData().getTranPos()
                        : null;
        double distance =
                playerPos != null && pointPos != null
                        ? playerPos.computeDistance(pointPos)
                        : Double.NaN;
        int currentScene = player.getScene() != null ? player.getScene().getId() : 0;

        emu.grasscutter.Grasscutter.getLogger()
                .info(
                        "[quest351] unlock-request uid={} requestedScene={} point={} currentScene={} playerPos={} pointPos={} distance={} previouslyUnlocked={} forceLocked={} statue={}",
                        player.getUid(),
                        req.getSceneId(),
                        req.getPointId(),
                        currentScene,
                        playerPos,
                        pointPos,
                        distance,
                        previouslyUnlocked,
                        forceLocked,
                        isStatue);

        boolean unlocked =
                TransPointUnlockHelper.unlock(
                        player, req.getSceneId(), req.getPointId(), isStatue);
        emu.grasscutter.Grasscutter.getLogger()
                .info(
                        "[quest351] unlock-result uid={} scene={} point={} unlocked={}",
                        player.getUid(),
                        req.getSceneId(),
                        req.getPointId(),
                        unlocked);
        // UnlockTransPointRsp has no known 7.1 CmdId, so the client never hears back and leaves the
        // point drawn as locked. Resend the scene's point list, as the statue auto-unlock does.
        if (unlocked) {
            player.sendPacket(
                    new emu.grasscutter.server.packet.send.PacketGetScenePointRsp(
                            player, req.getSceneId()));
        }
        player.sendPacket(
                new PacketUnlockTransPointRsp(
                        unlocked
                                ? RetcodeOuterClass.Retcode.RET_SUCC
                                : RetcodeOuterClass.Retcode.RET_FAIL));
    }
}
