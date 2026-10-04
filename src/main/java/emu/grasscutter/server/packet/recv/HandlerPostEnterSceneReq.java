package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.GAME;

import emu.grasscutter.game.ability.EscoffierSkillCookHelper;
import emu.grasscutter.game.player.EntryNotice;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PostEnterSceneReqOuterClass.PostEnterSceneReq;
import emu.grasscutter.server.born.BornIntroGate;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketCutsceneBeginNotify;
import emu.grasscutter.server.packet.send.PacketGetPlayerFriendListRsp;
import emu.grasscutter.server.packet.send.PacketPostEnterSceneRsp;

@Opcodes(PacketOpcodes.PostEnterSceneReq)
public class HandlerPostEnterSceneReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        PostEnterSceneReq req = PostEnterSceneReq.parseFrom(payload);

        var player = session.getPlayer();
        var scene = player.getScene();
        var questManager = player.getQuestManager();
        var freshPlayerBootstrap = BornIntroGate.isFreshPlayerBootstrap(session);

        // Native-selection and automatic/skip-intro births converge here. The first
        // PostEnterSceneRsp must reach the client before Quest 351's incremental start so AQ351 and
        // Paimon's quest actors initialize against a ready playable scene.
        if (freshPlayerBootstrap) {
            session.send(new PacketPostEnterSceneRsp(player));
            BornIntroGate.finishOnSceneReady(session);
        }

        switch (scene.getSceneType()) {
            case SCENE_ROOM ->
                    questManager.queueEvent(QuestContent.QUEST_CONTENT_ENTER_ROOM, scene.getId(), 0);
            case SCENE_WORLD -> {
                questManager.queueEvent(QuestContent.QUEST_CONTENT_ENTER_MY_WORLD, scene.getId());
                questManager.queueEvent(QuestContent.QUEST_CONTENT_ENTER_MY_WORLD_SCENE, scene.getId());
            }
            case SCENE_DUNGEON -> {
                var dungeonManager = scene.getDungeonManager();
                if (dungeonManager != null) dungeonManager.startDungeon();
            }
        }
        questManager.queueEvent(QuestContent.QUEST_CONTENT_LEAVE_SCENE, scene.getPrevScene());

        if (!freshPlayerBootstrap) {
            session.send(new PacketPostEnterSceneRsp(player));
        }

        // Escoffier's improvised cooking: lightly sync the weekly remainder after entering the scene so a
        // stale CannotCreateFood does not linger.
        EscoffierSkillCookHelper.syncToClient(player);
        EntryNotice.sendOnce(player);
        // The client only asks for friends and chat again after a teleport, so push both here or the
        // console and DPS bots are missing until then.
        session.send(new PacketGetPlayerFriendListRsp(player));
        session.getServer().getChatSystem().ensureServerConversation(player);

        // Fresh 7.1 starts the opening from AQ351/35104. A configured legacy first-login cutscene
        // here would create a second independent source for the same intro after quest bootstrap.
        if (!freshPlayerBootstrap) {
            this.playOpeningCutscene(player);
        }
    }

    /** Fired here rather than at login: a cutscene sent before the scene is up is discarded. */
    private void playOpeningCutscene(emu.grasscutter.game.player.Player player) {
        int cutscene = GAME.firstLoginCutscene;
        if (GAME.disableCutscenes || cutscene <= 0 || player.isPlayedFirstLoginCutscene()) return;

        player.setPlayedFirstLoginCutscene(true);
        player.save();
        player.sendPacket(new PacketCutsceneBeginNotify(cutscene));
    }
}
