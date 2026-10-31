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
                    var raw = req.getMark().getPos();
                    emu.grasscutter.Grasscutter.getLogger().info(
                            "Fishhook mark raw uid={} scene={} hasPos={} pos=({},{},{}) playerScene={} playerY={}",
                            player.getUid(), req.getMark().getSceneId(), req.getMark().hasPos(),
                            raw.getX(), raw.getY(), raw.getZ(),
                            player.getScene().getId(), player.getPosition().getY());
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

    /**
     * A map pin's Y is always zero in 7.1. Nearby original scene points provide a bounded
     * elevation estimate, reducing the unnecessary drop from Y=2000. This is not terrain
     * collision detection: distant or multi-level destinations still require native geometry.
     */
    static float estimatedAltitude(Position target, Iterable<Position> scenePoints, float fallback) {
        float best = Float.POSITIVE_INFINITY;
        for (var position : scenePoints) {
            if (position == null || !Float.isFinite(position.getY())) continue;
            double distance = Math.hypot(position.getX() - target.getX(),
                    position.getZ() - target.getZ());
            if (!Double.isFinite(distance) || distance > 250) continue;
            float candidate = position.getY() + 30f + (float) distance * 2f;
            best = Math.min(best, candidate);
        }
        return Float.isFinite(best) ? best : fallback;
    }

    static float safeSceneAltitude(int sceneId, Position target) {
        var scenePoints = GameData.getScenePointEntryMap().values().stream()
                .filter(entry -> entry.getSceneId() == sceneId)
                .map(entry -> entry.getPointData())
                .map(point -> point.getTranPos() != null ? point.getTranPos() : point.getPos())
                .toList();
        return estimatedAltitude(target, scenePoints, 2000f);
    }

    private void teleport(Player player, MapMark mapMark) {
        Position pos = mapMark.getPosition();
        float y = resolveTeleportHeight(mapMark.getName(),
                safeSceneAltitude(mapMark.getSceneId(), pos));
        emu.grasscutter.Grasscutter.getLogger().info(
                "Fishhook destination scene={} pos=({},{},{}) selectedY={}",
                mapMark.getSceneId(), pos.getX(), pos.getY(), pos.getZ(), y);
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
