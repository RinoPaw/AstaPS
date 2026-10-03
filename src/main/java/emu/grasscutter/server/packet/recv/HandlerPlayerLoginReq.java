package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.StatueActivationProbe;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.born.BornDataConfig;
import emu.grasscutter.server.born.BornDataHelper;
import emu.grasscutter.server.born.BornIntroGate;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.server.packet.send.PacketPlayerLoginRsp;

@Opcodes(PacketOpcodes.PlayerLoginReq)
public class HandlerPlayerLoginReq extends PacketHandler {
    private static final int FIRST_MAIN_QUEST = 351;

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        if (session.getAccount() == null) {
            session.close();
            return;
        }

        Player player = session.getPlayer();
        var intro = GAME_OPTIONS.newAccountIntro;
        boolean freshAccount = player.getAvatars().getAvatarCount() == 0;
        boolean nativeSelection = freshAccount && BornDataConfig.isSelectionMode() && intro.enabled;

        if (nativeSelection) {
            // Native mode leaves the account empty until the client chooses Aether/Lumine. No World
            // or quest state is created before SetPlayerBornDataReq.
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

        boolean autoBornNow = false;
        if (freshAccount) {
            if (BornDataConfig.isSelectionMode() && !intro.enabled) {
                Grasscutter.getLogger()
                        .warn(
                                "[born-flow] uid={} config-born mode=select but newAccountIntro is disabled; using automatic birth.",
                                player.getUid());
            }

            int avatarId = BornDataHelper.resolveConfiguredAvatarId();
            String nickname = BornDataHelper.resolveConfiguredNickname(player);
            if (!BornDataHelper.completeBirth(player, avatarId, nickname)) {
                Grasscutter.getLogger()
                        .error(
                                "[born-flow] uid={} automatic birth failed; closing the incomplete session.",
                                player.getUid());
                session.close();
                return;
            }

            // Register the fresh-player lifecycle before Player.onLogin. This lets HomeWorld and
            // scene-entry code recognize a fresh account while keeping the ordinary login scene
            // packet intact; only Quest 351 is deferred to PostEnterSceneRsp.
            BornIntroGate.armSceneReady(session);
            BornDataHelper.sendWelcomeMail(player);
            autoBornNow = true;
            Grasscutter.getLogger()
                    .info(
                            "[born-flow] uid={} mode=auto; born as avatar {} and skipping character-selection/native intro visuals.",
                            player.getUid(),
                            avatarId);
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
        keepLegacyStarterStatueLocked(player);

        // PlayerProgressManager's legacy compatibility path has already seeded/finished statue
        // state by this point. Restore every still-locked statue quest now, after point 7/area 1
        // cleanup, and push the corrected UNFINISHED state before gameplay starts.
        StatueActivationProbe.restoreLockedActivationQuests(player, true);

        if (autoBornNow) {
            // World/login state is complete, but fresh-player quests remain gated until the client's
            // first PostEnterSceneReq has been acknowledged.
            BornIntroGate.markWorldLoginComplete(session);
        }

        session.send(new PacketPlayerLoginRsp(session));
    }

    private static void keepLegacyStarterStatueLocked(Player player) {
        // PlayerProgressManager still carries legacy starter seeds for scene 3 point 7 and area 1.
        // This probe branch deliberately removes both after login so the same Statue of the Seven
        // starts with its point locked and its map fog still present. Do not force-lock the point:
        // native unlock must remain free to add it normally during the current session.
        boolean removedPoint = player.getUnlockedScenePoints(3).remove(7);
        boolean removedArea = player.getUnlockedSceneAreas(3).remove(1);
        player.getForceLockedScenePoints(3).remove(7);
        if (removedPoint || removedArea) {
            player.save();
            Grasscutter.getLogger()
                    .info(
                            "[statue-probe] uid={} removed legacy starter unlocks point=3:7 area=3:1 pointRemoved={} areaRemoved={}.",
                            player.getUid(),
                            removedPoint,
                            removedArea);
        }
    }
}
