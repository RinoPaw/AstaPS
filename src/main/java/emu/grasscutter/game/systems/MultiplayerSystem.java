package emu.grasscutter.game.systems;

import emu.grasscutter.game.CoopRequest;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.Player.SceneLoadState;
import emu.grasscutter.game.props.EnterReason;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.proto.EnterTypeOuterClass.EnterType;
import emu.grasscutter.net.proto.PlayerApplyEnterMpResultNotifyOuterClass;
import emu.grasscutter.net.proto.ReasonOuterClass;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.send.*;
import java.util.concurrent.ConcurrentHashMap;

public class MultiplayerSystem extends BaseGameSystem {
    private final ConcurrentHashMap<Integer, Integer> guestHostUids = new ConcurrentHashMap<>();

    public MultiplayerSystem(GameServer server) {
        super(server);
    }

    public void applyEnterMp(Player player, int targetUid) {
        normalizeStaleMultiplayerWorld(player);

        Player target = getServer().getPlayerByUid(targetUid);
        if (target == null) {
            player.sendPacket(
                    new PacketPlayerApplyEnterMpResultNotify(
                            targetUid,
                            "",
                            false,
                            ReasonOuterClass.Reason.Reason_PLAYER_CANNOT_ENTER_MP));
            return;
        }

        if (player.getWorld() == null || player.getWorld().isMultiplayer()) {
            return;
        }

        CoopRequest request = target.getCoopRequests().get(player.getUid());
        if (request != null && !request.isExpired()) {
            return;
        }

        request = new CoopRequest(player);
        target.getCoopRequests().put(player.getUid(), request);
        target.sendPacket(new PacketPlayerApplyEnterMpNotify(player));
    }

    public void applyEnterMpReply(Player hostPlayer, int applyUid, boolean isAgreed) {
        CoopRequest request = hostPlayer.getCoopRequests().get(applyUid);
        if (request == null || request.isExpired()) {
            return;
        }

        Player requester = request.getRequester();
        hostPlayer.getCoopRequests().remove(applyUid);
        normalizeStaleMultiplayerWorld(requester);

        if (requester.getWorld() == null || requester.getWorld().isMultiplayer()) {
            requester.sendPacket(
                    new PacketPlayerApplyEnterMpResultNotify(
                            hostPlayer,
                            false,
                            ReasonOuterClass.Reason.Reason_PLAYER_CANNOT_ENTER_MP));
            return;
        }

        requester.sendPacket(
                new PacketPlayerApplyEnterMpResultNotify(
                        hostPlayer, isAgreed, ReasonOuterClass.Reason.Reason_PLAYER_JUDGE));

        if (!isAgreed) {
            return;
        }

        if (!hostPlayer.getWorld().isMultiplayer()) {
            World world = new World(hostPlayer, true);
            world.addPlayer(hostPlayer);

            hostPlayer.sendPacket(
                    new PacketPlayerEnterSceneNotify(
                            hostPlayer,
                            hostPlayer,
                            EnterType.EnterType_ENTER_SELF,
                            EnterReason.HostFromSingleToMp,
                            hostPlayer.getScene().getId(),
                            hostPlayer.getPosition()));
        }

        requester.getPosition().set(hostPlayer.getPosition());
        requester.getRotation().set(hostPlayer.getRotation());
        requester.setSceneId(hostPlayer.getSceneId());

        hostPlayer.getWorld().addPlayer(requester);
        guestHostUids.put(requester.getUid(), hostPlayer.getUid());

        requester.sendPacket(
                new PacketPlayerEnterSceneNotify(
                        requester,
                        hostPlayer,
                        EnterType.EnterType_ENTER_OTHER,
                        EnterReason.TeamJoin,
                        hostPlayer.getScene().getId(),
                        hostPlayer.getPosition()));
    }

    public boolean leaveCoop(Player player) {
        guestHostUids.remove(player.getUid());

        if (player.getCurHomeWorld().isInHome(player)) {
            return false;
        }

        if (player.getWorld() == null || !player.getWorld().isMultiplayer()) {
            return false;
        }

        for (Player p : player.getWorld().getPlayers()) {
            if (p.getSceneLoadState() != SceneLoadState.LOADED) {
                return false;
            }
        }

        World world = new World(player);
        world.addPlayer(player);

        player.sendPacket(
                new PacketPlayerEnterSceneNotify(
                        player,
                        EnterType.EnterType_ENTER_OTHER,
                        EnterReason.TeamBack,
                        player.getScene().getId(),
                        player.getPosition()));
        player.sendPacket(new PacketEnterScenePeerNotify(player));

        return true;
    }

    public boolean kickPlayer(Player player, int targetUid) {
        if (player.getWorld() == null
                || !player.getWorld().isMultiplayer()
                || player.getWorld().getHost() != player) {
            return false;
        }

        Player victim = player.getServer().getPlayerByUid(targetUid);
        if (victim == null || victim == player) {
            return false;
        }

        guestHostUids.remove(victim.getUid());

        if (victim.getSceneLoadState() != SceneLoadState.LOADED) {
            return false;
        }

        World world = new World(victim);
        world.addPlayer(victim);

        victim.sendPacket(
                new PacketPlayerEnterSceneNotify(
                        victim,
                        EnterType.EnterType_ENTER_OTHER,
                        EnterReason.TeamKick,
                        victim.getScene().getId(),
                        victim.getPosition()));
        victim.sendPacket(new PacketEnterScenePeerNotify(victim));
        return true;
    }

    public World reconcileMultiplayerWorld(Player player) {
        Integer hostUid = guestHostUids.get(player.getUid());
        if (hostUid == null) {
            return player.getWorld();
        }

        Player host = getServer().getPlayerByUid(hostUid);
        if (host == null || host == player) {
            guestHostUids.remove(player.getUid());
            return player.getWorld();
        }

        World hostWorld = host.getWorld();
        if (hostWorld == null
                || !hostWorld.isMultiplayer()
                || hostWorld.getHost() != host
                || !hostWorld.getPlayers().contains(host)) {
            guestHostUids.remove(player.getUid());
            return player.getWorld();
        }

        if (player.getWorld() != hostWorld) {
            hostWorld.addPlayer(player, host.getSceneId());
            guestHostUids.put(player.getUid(), host.getUid());
        }

        return hostWorld;
    }

    private void normalizeStaleMultiplayerWorld(Player player) {
        World previousWorld = player.getWorld();
        if (previousWorld == null
                || !previousWorld.isMultiplayer()
                || previousWorld.getPlayerCount() != 1
                || previousWorld.getHost() != player) {
            return;
        }

        World singlePlayerWorld = new World(player);
        singlePlayerWorld.addPlayer(player);
        guestHostUids.remove(player.getUid());
    }
}
