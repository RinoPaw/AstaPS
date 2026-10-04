package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.ACCOUNT;

import emu.grasscutter.*;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.event.game.PlayerCreationEvent;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.net.proto.GetPlayerTokenReqOuterClass.GetPlayerTokenReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.PacketGetPlayerTokenRsp;
import emu.grasscutter.utils.*;
import emu.grasscutter.utils.helpers.ByteHelper;
import java.nio.ByteBuffer;
import java.security.Signature;
import java.util.concurrent.ThreadPoolExecutor;
import javax.crypto.Cipher;

@Opcodes(PacketOpcodes.GetPlayerTokenReq)
public class HandlerGetPlayerTokenReq extends PacketHandler {
    /**
     * Ban end time written for an account taken down by an IP ban.
     *
     * <p>An IP ban has no end of its own, but the ban screen needs one, so this stands in for "not
     * coming back". banEndTime is an int of epoch seconds, so the furthest it can express is
     * 2038-01-19 - upstream writes a 2099 timestamp here, which silently overflows. /unbanip is
     * what actually lifts it.
     */
    private static final int IP_BAN_END_TIME = Integer.MAX_VALUE;


    // Read with ProtoRead rather than parseFrom: a mismatched field type makes parseFrom throw
    // instead of returning the fields that still line up. The numbers come from the generated
    // class so they follow the protocol; they used to be pinned to a 7.0 capture (2, 6, 588, 932),
    // which a 7.1 client does not use, so every token check failed and the session was closed.
    private static final int F_ACCOUNT_UID = GetPlayerTokenReq.ACCOUNT_UID_FIELD_NUMBER;
    private static final int F_ACCOUNT_TOKEN = GetPlayerTokenReq.ACCOUNT_TOKEN_FIELD_NUMBER;
    private static final int F_KEY_ID = GetPlayerTokenReq.KEY_ID_FIELD_NUMBER;
    private static final int F_CLIENT_RAND_KEY = GetPlayerTokenReq.CLIENT_RAND_KEY_FIELD_NUMBER;

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var accountId = ProtoRead.string(payload, F_ACCOUNT_UID);
        var accountToken = ProtoRead.string(payload, F_ACCOUNT_TOKEN);
        var clientRandKey = ProtoRead.string(payload, F_CLIENT_RAND_KEY);
        var keyId = (int) ProtoRead.varint(payload, F_KEY_ID);

        var account = DispatchUtils.authenticate(accountId, accountToken);

        if (account == null && !DebugConstants.ACCEPT_CLIENT_TOKEN) {
            Grasscutter.getLogger()
                    .warn(
                            "Token check failed for account '{}' from {} - closing the session.",
                            accountId,
                            session.getAddress());
            session.close();
            return;
        } else if (account == null && DebugConstants.ACCEPT_CLIENT_TOKEN) {
            account = DispatchUtils.getAccountById(accountId);
            if (account == null) {
                session.close();
                return;
            }
        }

        session.setAccount(account);

        boolean kicked = false;
        var exists = Grasscutter.getGameServer().getPlayerByAccountId(accountId);
        if (exists != null) {
            var existsSession = exists.getSession();
            if (existsSession != session) {
                exists.onLogout();
                existsSession.close();
                Grasscutter.getLogger()
                    .warn("Player {} was kicked due to duplicated login", account.getUsername());
                kicked = true;
            }
        }

        if (!kicked) {

            if (ACCOUNT.maxPlayer > -1
                && Grasscutter.getGameServer().getPlayers().size() >= ACCOUNT.maxPlayer) {
                session.close();
                return;
            }
        }

        var event = new PlayerCreationEvent(session, Player.class);
        event.call();

        var player = DatabaseHelper.getPlayerByAccount(account, event.getPlayerClass());

        if (player == null) {
            var nextPlayerUid =
                DatabaseHelper.getNextPlayerId(session.getAccount().getReservedPlayerUid());

            player =
                event.getPlayerClass().getDeclaredConstructor(GameSession.class).newInstance(session);

            DatabaseHelper.generatePlayerUid(player, nextPlayerUid);
        }

        session.setPlayer(player);

        // An IP ban takes the account with it: without that, the same person just registers again
        // from the same address. The account ban is what the client is actually told about, since
        // it is the one the ban screen can explain.
        var clientIp = session.getAddress().getAddress().getHostAddress();
        if (DatabaseHelper.isIpBanned(clientIp)) {
            var bannedAccount = session.getAccount();
            if (!bannedAccount.isBanned()) {
                bannedAccount.setBanned(true);
                bannedAccount.setBannedByIp(clientIp);
                bannedAccount.setBanReason(DatabaseHelper.IP_BAN_REASON_PREFIX + clientIp);
                bannedAccount.setBanStartTime((int) (System.currentTimeMillis() / 1000));
                bannedAccount.setBanEndTime(IP_BAN_END_TIME);
                bannedAccount.save();
                Grasscutter.getLogger()
                    .warn("IP {} is banned; account {} was banned with it.",
                        clientIp, bannedAccount.getUsername());
            }
        }

        if (session.getAccount().isBanned()) {
            session.setState(SessionState.ACCOUNT_BANNED);
            String banReason = session.getAccount().getBanReason();
            if (banReason == null || banReason.trim().isEmpty()) {
                banReason = "FORBID_CHEATING_PLUGINS";
            }
            session.send(
                new PacketGetPlayerTokenRsp(
                    session, 21, banReason, session.getAccount().getBanEndTime()));
            return;
        }

        // Refuse the login while the server is already at its player limit. Unlike the guards
        // below, the client has its own wording for this one, so it gets a plain retcode.
        if (ACCOUNT.maxPlayer > -1
                && Grasscutter.getGameServer().getPlayers().size() >= ACCOUNT.maxPlayer) {
            session.setState(SessionState.SERVER_MAX_PLAYER_OVERFLOW);
            session.send(
                new PacketGetPlayerTokenRsp(session, Retcode.RET_MP_ALLOW_ENTER_PLAYER_FULL));
            Grasscutter.getLogger()
                .info("Refused uid {}: the server is full.", session.getPlayer().getUid());
            return;
        }

        // Refuse the login while the save queues are backed up. Letting a player in at this point
        // makes it worse: loading them is itself database work, and every save they then generate
        // joins the same queue. Turning them away is what lets it drain.
        if (isDatabaseOverloaded()) {
            session.setState(SessionState.DB_OVERLOAD);
            session.send(new PacketGetPlayerTokenRsp(session, "Server is overloaded, try again shortly"));
            Grasscutter.getLogger()
                .warn(
                    "Refused uid {}: database queues are backed up (default {}/{}, account {}/{},"
                        + " item {}/{}, group {}/{}).",
                    session.getPlayer().getUid(),
                    queueSize(DatabaseHelper.getEventExecutor()),
                    DatabaseHelper.DEFAULT_QUEUE_CAPACITY,
                    queueSize(DatabaseHelper.getEventExecutorAccount()),
                    DatabaseHelper.ACCOUNT_QUEUE_CAPACITY,
                    queueSize(DatabaseHelper.getEventExecutorItem()),
                    DatabaseHelper.ITEM_QUEUE_CAPACITY,
                    queueSize(DatabaseHelper.getEventExecutorGroup()),
                    DatabaseHelper.GROUP_QUEUE_CAPACITY);
            return;
        }

        try {
            player.loadFromDatabase();
        } catch (IllegalStateException e) {
            // Load failed or timed out: drop this one client rather than leave it hanging.
            Grasscutter.getLogger().error("Refused uid {}: {}", player.getUid(), e.getMessage(), e.getCause());
            session.close();
            return;
        }

        if (Grasscutter.getConfig().server.game.useXorEncryption) {
            session.setState(SessionState.WAITING_FOR_LOGIN);

            if (keyId > 0) {
                var encryptSeed = session.getEncryptSeed();
                try {
                    var cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                    cipher.init(Cipher.DECRYPT_MODE, Crypto.CUR_SIGNING_KEY);

                    var clientSeedEncrypted = Utils.base64Decode(clientRandKey);
                    var clientSeed = ByteBuffer.wrap(cipher.doFinal(clientSeedEncrypted)).getLong();

                    var combinedSeed = encryptSeed ^ clientSeed;
                    var seedBytes = ByteBuffer.wrap(new byte[8]).putLong(combinedSeed).array();

                    cipher.init(Cipher.ENCRYPT_MODE, Crypto.EncryptionKeys.get(keyId));
                    var seedEncrypted = cipher.doFinal(seedBytes);

                    var privateSignature = Signature.getInstance("SHA256withRSA");
                    privateSignature.initSign(Crypto.CUR_SIGNING_KEY);
                    privateSignature.update(seedBytes);

                    // Exactly ONE response per request. Sending several candidates back to back was
                    // meant to test the field map faster, but a real server answers once, and a
                    // duplicate response to a finished request is its own reason for a client to
                    // give up - which would mask the very thing the candidates were testing. The
                    // client reopens this exchange every 30-60 seconds by itself, so one candidate
                    // per request still covers the whole rotation in a few minutes.
                    var rsp = new PacketGetPlayerTokenRsp(
                            session,
                            Utils.base64Encode(seedEncrypted),
                            Utils.base64Encode(privateSignature.sign()),
                            keyId);
                    session.send(rsp);
                    switchWireKey(session);
                } catch (Exception ignored) {

                    Grasscutter.getLogger().error("GetPlayerTokenReq RSA failed (key_id={}, clientRandKey len={}): {}",
                        keyId,
                        clientRandKey.isEmpty() ? 0 : Utils.base64Decode(clientRandKey).length,
                        ignored.getClass().getSimpleName() + ": " + ignored.getMessage());
                    var clientBytes = Utils.base64Decode(clientRandKey);
                    var seed = ByteHelper.longToBytes(encryptSeed);
                    Crypto.xor(clientBytes, seed);

                    var base64str = Utils.base64Encode(clientBytes);
                    session.send(new PacketGetPlayerTokenRsp(session, base64str, "bm90aGluZyBoZXJl", keyId));
                    switchWireKey(session);
                }
            } else {
                session.send(new PacketGetPlayerTokenRsp(session, keyId));
            }
        } else {
            session.setState(SessionState.WAITING_FOR_LOGIN);
            session.send(new PacketGetPlayerTokenRsp(session, keyId));
        }
    }

    /**
     * Moves the connection onto the negotiated session key.
     *
     * <p>This used to be skipped on 7.x, because the client could not read our response and so never
     * moved with us, and switching alone would have turned its pings - the only signal still coming
     * back - into noise. That trade is gone: the session now works out which key a frame arrived
     * under instead of assuming, so it follows the client either way and nothing is lost by
     * switching. Keeping the skip would be actively harmful now, because the moment the field map is
     * right the client switches and the server has to be there with it.
     */
    private static void switchWireKey(GameSession session) {
        session.setUseSecretKey(true);
    }

    private static boolean isDatabaseOverloaded() {
        return DatabaseHelper.isThreadPoolOverloaded(
                    (ThreadPoolExecutor) DatabaseHelper.getEventExecutor(),
                    DatabaseHelper.DEFAULT_QUEUE_CAPACITY)
                || DatabaseHelper.isThreadPoolOverloaded(
                    (ThreadPoolExecutor) DatabaseHelper.getEventExecutorAccount(),
                    DatabaseHelper.ACCOUNT_QUEUE_CAPACITY)
                || DatabaseHelper.isThreadPoolOverloaded(
                    (ThreadPoolExecutor) DatabaseHelper.getEventExecutorItem(),
                    DatabaseHelper.ITEM_QUEUE_CAPACITY)
                || DatabaseHelper.isThreadPoolOverloaded(
                    (ThreadPoolExecutor) DatabaseHelper.getEventExecutorGroup(),
                    DatabaseHelper.GROUP_QUEUE_CAPACITY);
    }

    private static int queueSize(java.util.concurrent.ExecutorService executor) {
        return ((ThreadPoolExecutor) executor).getQueue().size();
    }
}
