package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.born.BornDataHelper;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.server.packet.send.PacketPlayerLoginRsp;

@Opcodes(PacketOpcodes.PlayerLoginReq)
public class HandlerPlayerLoginReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        if (session.getAccount() == null) {
            session.close();
            return;
        }

        Player player = session.getPlayer();
        var intro = GAME_OPTIONS.newAccountIntro;

        if (player.getAvatars().getAvatarCount() == 0) {
            // A brand-new account must complete the client-driven Traveler creation handshake.
            // Do not invent a Traveler when the protocol/configuration is incomplete: that hides
            // the actual compatibility problem and permanently changes the account state.
            if (!intro.enabled) {
                Grasscutter.getLogger()
                        .error(
                                "[intro] account {} has no character, but newAccountIntro is disabled; refusing automatic Traveler creation.",
                                session.getAccount().getUsername());
                session.close();
                return;
            }

            int notifyCmdId = intro.doSetPlayerBornDataNotify;
            if (notifyCmdId <= 0) {
                Grasscutter.getLogger()
                        .error(
                                "[intro] DoSetPlayerBornDataNotify CmdId is unknown ({}); refusing character creation until the real 7.1 opcode is known.",
                                notifyCmdId);
                session.close();
                return;
            }

            // Negative entries in some 7.1 opcode tables are unresolved placeholders, not signed
            // wire CmdIds. Only an explicitly known positive opcode is safe to send.
            session.setState(SessionState.PICKING_CHARACTER);
            session.send(new BasePacket(notifyCmdId));
            Grasscutter.getLogger()
                    .info(
                            "[intro] new account, waiting for client character creation (notify cmdId={}).",
                            notifyCmdId);

            session.send(new PacketPlayerLoginRsp(session));
            return;
        }

        // Existing accounts may predate the explicit main-character marker. This only repairs the
        // marker for an avatar that already exists; it never creates a Traveler.
        BornDataHelper.ensureMainCharacter(player);

        player.onLogin();
        session.send(new PacketPlayerLoginRsp(session));
    }
}
