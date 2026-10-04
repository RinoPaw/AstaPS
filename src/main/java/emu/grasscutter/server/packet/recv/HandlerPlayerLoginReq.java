package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.GAME;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.born.BornDataHelper;
import emu.grasscutter.server.born.BornIntroGate;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.server.packet.send.PacketPlayerLoginRsp;

@Opcodes(PacketOpcodes.PlayerLoginReq)
public class HandlerPlayerLoginReq extends PacketHandler {
    private static final int FIRST_MAIN_QUEST = 351;
    private static final int STARTER_SCENE_ID = 3;
    private static final int STARTER_STATUE_POINT_ID = 7;

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        if (session.getAccount() == null) {
            session.close();
            return;
        }

        Player player = session.getPlayer();
        var intro = GAME.newAccountIntro;
        boolean freshAccount = player.getAvatars().getAvatarCount() == 0;
        boolean skipIntro = freshAccount && intro.skip;

        if (freshAccount && intro.enabled && !skipIntro) {
            // Native selection keeps the account unborn until the client submits SetPlayerBornDataReq.
            // World/scene initialization therefore remains outside this login request.
            session.setState(SessionState.PICKING_CHARACTER);
            int notifyCmdId =
                    intro.doSetPlayerBornDataNotify > 0
                            ? intro.doSetPlayerBornDataNotify
                            : PacketOpcodes.DoSetPlayerBornDataNotify;
            if (notifyCmdId > 0) {
                session.send(new BasePacket(notifyCmdId));
            }
            Grasscutter.getLogger()
                    .info(
                            "[born-flow] uid={} mode=select; waiting for native character selection (notify cmdId={}).",
                            player.getUid(),
                            notifyCmdId > 0 ? notifyCmdId : "unsent");
            session.send(new PacketPlayerLoginRsp(session));
            return;
        }

        boolean starterStatueUnlockedBeforeLogin =
                player.getUnlockedScenePoints(STARTER_SCENE_ID).contains(STARTER_STATUE_POINT_ID);
        boolean starterStatueForceLockedBeforeLogin =
                player.isScenePointForceLocked(STARTER_SCENE_ID, STARTER_STATUE_POINT_ID);

        boolean autoBornNow = false;
        if (freshAccount) {
            int avatarId = BornDataHelper.resolveAutomaticAvatarId();
            String nickname = BornDataHelper.resolveAutomaticNickname();
            if (!BornDataHelper.completeBirth(player, avatarId, nickname)) {
                Grasscutter.getLogger()
                        .error(
                                "[born-flow] uid={} automatic birth failed; closing incomplete session.",
                                player.getUid());
                session.close();
                return;
            }

            // Register the fresh-player lifecycle before Player.onLogin so HomeWorld/scene code can
            // recognize this first login. The ordinary login scene packet is retained; only Quest
            // 351 is delayed until the first PostEnterSceneRsp.
            BornIntroGate.armSceneReady(session);
            BornDataHelper.sendWelcomeMail(player);
            autoBornNow = true;

            Grasscutter.getLogger()
                    .info(
                            "[born-flow] uid={} mode=auto avatar={} skipIntro={} introEnabled={}; visuals bypassed, scene-ready Quest bootstrap preserved.",
                            player.getUid(),
                            avatarId,
                            skipIntro,
                            intro.enabled);
        } else {
            BornDataHelper.ensureMainCharacter(player);
            var quest351 = player.getQuestManager().getMainQuestById(FIRST_MAIN_QUEST);
            if (quest351 != null && !quest351.getActiveQuests().isEmpty()) {
                Grasscutter.getLogger()
                        .info(
                                "[intro] existing login uid={} has active quest 351; QuestManager.onLogin will rewind it.",
                                player.getUid());
            }
        }

        player.onLogin();
        restoreStarterStatueState(
                player, starterStatueUnlockedBeforeLogin, starterStatueForceLockedBeforeLogin);

        if (autoBornNow) {
            // World/login state is complete. Fresh-player quests remain gated until the client's
            // first PostEnterSceneReq is acknowledged.
            BornIntroGate.markWorldLoginComplete(session);
        }

        session.send(new PacketPlayerLoginRsp(session));
    }

    /**
     * Legacy progress initialization still seeds scene 3 point 7 during Player.onLogin. Preserve the
     * persisted pre-login state so locked accounts stay locked while genuinely unlocked old accounts
     * are not regressed.
     */
    private static void restoreStarterStatueState(
            Player player, boolean wasUnlocked, boolean wasForceLocked) {
        var unlocked = player.getUnlockedScenePoints(STARTER_SCENE_ID);
        var forceLocked = player.getForceLockedScenePoints(STARTER_SCENE_ID);
        boolean changed =
                unlocked.contains(STARTER_STATUE_POINT_ID) != wasUnlocked
                        || forceLocked.contains(STARTER_STATUE_POINT_ID) != wasForceLocked;

        if (wasUnlocked) unlocked.add(STARTER_STATUE_POINT_ID);
        else unlocked.remove(STARTER_STATUE_POINT_ID);

        if (wasForceLocked) forceLocked.add(STARTER_STATUE_POINT_ID);
        else forceLocked.remove(STARTER_STATUE_POINT_ID);

        if (changed) player.save();
    }
}
