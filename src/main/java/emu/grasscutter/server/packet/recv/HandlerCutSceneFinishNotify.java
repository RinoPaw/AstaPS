package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.CutSceneFinishNotifyOuterClass.CutSceneFinishNotify;
import emu.grasscutter.server.born.BornIntroGate;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketCutSceneEndNotify;

@Opcodes(PacketOpcodes.CutSceneFinishNotify)
public class HandlerCutSceneFinishNotify extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        CutSceneFinishNotify req =
                CutSceneFinishNotify.parseFrom(payload == null ? new byte[0] : payload);
        int cutsceneId = req.getCutsceneId();
        var player = session.getPlayer();
        if (player == null) return;

        var quest35104 = player.getQuestManager().getQuestById(35104);
        emu.grasscutter.Grasscutter.getLogger()
                .info(
                        "[quest351] cutscene-finish uid={} cutscene={} quest35104={} payloadBytes={}",
                        player.getUid(),
                        cutsceneId,
                        quest35104 != null ? quest35104.getState() : null,
                        payload != null ? payload.length : 0);

        // Preserve the ordinary 7.1 cutscene acknowledgement path.
        session.send(new PacketCutSceneEndNotify(cutsceneId));
        player.getTowerManager().onMidHalfCutsceneFinished(cutsceneId);

        // Native fresh-born intro did not emit this in runtime traces; keep it diagnostic only.
        BornIntroGate.noteCutsceneFinish(session, cutsceneId, payload);
    }
}
