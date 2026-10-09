package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.GadgetInteractReq)
public class HandlerGadgetInteractReq extends PacketHandler {
    static boolean matchesQuestGadget(int sceneGadgetId, int requestedGadgetId) {
        return sceneGadgetId > 0 && sceneGadgetId == requestedGadgetId;
    }

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        GadgetInteractReq req = GadgetInteractReq.parseFrom(payload);
        var player = session.getPlayer();
        var scene = player.getScene();
        var target = scene == null ? null : scene.getEntityById(req.getGadgetEntityId());

        // GadgetInteractReq carries an entity ID and a gadget-data ID.
        // A non-existent entity or a mismatched client gadget ID must not
        // advance a story objective before any interaction happened.
        boolean matchesQuestTarget = target instanceof EntityGadget gadget
                && matchesQuestGadget(gadget.getGadgetId(), req.getGadgetId());

        if (scene != null) player.interactWith(req.getGadgetEntityId(), req);
        if (matchesQuestTarget) {
            player.getQuestManager().queueEvent(
                    QuestContent.QUEST_CONTENT_INTERACT_GADGET, req.getGadgetId());
        }
    }
}
