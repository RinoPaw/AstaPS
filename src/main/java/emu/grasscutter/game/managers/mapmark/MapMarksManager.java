package emu.grasscutter.game.managers.mapmark;

import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.MapMarkPointTypeOuterClass.MapMarkPointType;
import emu.grasscutter.net.proto.MarkMapReqOuterClass.MarkMapReq;
import emu.grasscutter.net.proto.MarkMapReqOuterClass.MarkMapReq.Operation;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import emu.grasscutter.server.packet.send.*;
import java.util.Map;

public class MapMarksManager extends BasePlayerManager {
    public static final int mapMarkMaxCount = 150;

    public MapMarksManager(Player player) {
        super(player);
    }

    public Map<String, MapMark> getMapMarks() {
        return this.getPlayer().getMapMarks();
    }

    public void handleMapMarkReq(MarkMapReq req) {
        Operation op = req.getOp();
        switch (op) {
            case Operation_ADD -> {
                MapMark createMark = new MapMark(req.getMark());
                // keep teleporting functionality on fishhook mark.
                if (Configuration.GAME_OPTIONS.fishhookTeleport
                        && createMark.getMapMarkPointType() == MapMarkPointType.MapMarkPointType_FISH_POOL) {
                    this.teleport(player, createMark);
                    return;
                }
                this.addMapMark(createMark);
            }
            case Operation_MOD -> {
                MapMark oldMark = new MapMark(req.getOld());
                this.removeMapMark(oldMark.getPosition());
                MapMark newMark = new MapMark(req.getMark());
                this.addMapMark(newMark);
            }
            case Operation_DEL -> {
                MapMark deleteMark = new MapMark(req.getMark());
                this.removeMapMark(deleteMark.getPosition());
            }
        }
        if (op != Operation.Operation_GET) {
            this.save();
        }
        player.getSession().send(new PacketMarkMapRsp(this.getMapMarks()));
    }

    public String getMapMarkKey(Position position) {
        return "x" + (int) position.getX() + "z" + (int) position.getZ();
    }

    public void removeMapMark(Position position) {
        this.getMapMarks().remove(this.getMapMarkKey(position));
    }

    public void addMapMark(MapMark mapMark) {
        if (this.getMapMarks().size() < mapMarkMaxCount) {
            this.getMapMarks().put(this.getMapMarkKey(mapMark.getPosition()), mapMark);
        }
    }

    /** Map pins supply X/Z but no reliable terrain elevation. Explicit names still override Y. */
    static float resolveTeleportHeight(String name, float safeAltitude) {
        if (name != null) {
            try {
                float requested = Float.parseFloat(name.trim());
                if (Float.isFinite(requested)) return requested;
            } catch (NumberFormatException ignored) {
                // A normal marker label is not an altitude.
            }
        }
        return safeAltitude;
    }

    /** Stay above the scene's known teleport points when the player did not specify Y. */
    static float safeSceneAltitude(int sceneId) {
        float highest = 0;
        for (var entry : GameData.getScenePointEntryMap().values()) {
            if (entry.getSceneId() != sceneId) continue;
            var point = entry.getPointData();
            var position = point.getTranPos() != null ? point.getTranPos() : point.getPos();
            if (position == null || !Float.isFinite(position.getY())) continue;
            highest = Math.max(highest, position.getY());
        }
        return Math.max(2000f, highest + 300f);
    }

    private void teleport(Player player, MapMark mapMark) {
        float y = resolveTeleportHeight(mapMark.getName(), safeSceneAltitude(mapMark.getSceneId()));
        Position pos = mapMark.getPosition();
        player
                .getWorld()
                .transferPlayerToScene(
                        player,
                        mapMark.getSceneId(),
                        TeleportType.MAP,
                        new Position(pos.getX(), y, pos.getZ()));
        player.getScene().broadcastPacket(new PacketSceneEntityAppearNotify(player));
    }
}
