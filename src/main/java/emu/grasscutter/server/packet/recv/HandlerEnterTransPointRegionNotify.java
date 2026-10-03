package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.EnterTransPointRegionNotifyOuterClass.EnterTransPointRegionNotify;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.utils.Utils;
import java.util.ArrayList;
import java.util.List;

@Opcodes(PacketOpcodes.EnterTransPointRegionNotify)
public class HandlerEnterTransPointRegionNotify extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        String hex = Utils.bytesToHex(payload == null ? new byte[0] : payload);
        String tags = dumpVarintTags(payload);
        int uid = session.getPlayer() != null ? session.getPlayer().getUid() : 0;
        try {
            EnterTransPointRegionNotify notify = EnterTransPointRegionNotify.parseFrom(payload);
            int sceneId = notify.getSceneId();
            int pointId = notify.getPointId();
            Grasscutter.getLogger()
                    .info(
                            "EnterTransPoint uid={} sceneId={} pointId={} hex={} tags={}",
                            uid,
                            sceneId,
                            pointId,
                            hex,
                            tags);

            var player = session.getPlayer();

            // EnterTrans is only a proximity/region signal. A locked Statue of the Seven stays
            // locked here; activation is owned by Quest 303 and NpcTalkReq.
            if (sceneId > 0
                    && pointId > 0
                    && player != null
                    && !player.isScenePointForceLocked(sceneId, pointId)
                    && player.getUnlockedScenePoints(sceneId).contains(pointId)) {
                session.send(
                        new emu.grasscutter.server.packet.send.PacketScenePointUnlockNotify(
                                sceneId, pointId));
                // Re-push the post-unlock Talk gate/goddess state for an already unlocked statue.
                player.getProgressManager().refreshStatueTalkGate(sceneId, pointId);
                try {
                    player.getScene().loadNpcForPlayerEnter(player);
                } catch (Throwable ignored) {
                }
            }
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .warn(
                            "EnterTransPoint parse failed uid={} err={} hex={} tags={}",
                            uid,
                            e.toString(),
                            hex,
                            tags);
        }
        session.getPlayer().getSotsManager().handleEnterTransPointRegionNotify();
    }

    /** Dump protobuf field_number->varint for quick wire-layout checks. */
    private static String dumpVarintTags(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return "[]";
        }
        List<String> out = new ArrayList<>();
        int i = 0;
        try {
            while (i < payload.length) {
                long key = 0;
                int shift = 0;
                while (i < payload.length) {
                    int b = payload[i++] & 0xff;
                    key |= (long) (b & 0x7f) << shift;
                    if ((b & 0x80) == 0) {
                        break;
                    }
                    shift += 7;
                    if (shift > 63) {
                        return out + "+badKey";
                    }
                }
                int field = (int) (key >>> 3);
                int wire = (int) (key & 0x7);
                if (wire == 0) {
                    long val = 0;
                    shift = 0;
                    while (i < payload.length) {
                        int b = payload[i++] & 0xff;
                        val |= (long) (b & 0x7f) << shift;
                        if ((b & 0x80) == 0) {
                            break;
                        }
                        shift += 7;
                        if (shift > 63) {
                            return out + "+badVarint";
                        }
                    }
                    out.add(field + "=" + val);
                } else if (wire == 2) {
                    long len = 0;
                    shift = 0;
                    while (i < payload.length) {
                        int b = payload[i++] & 0xff;
                        len |= (long) (b & 0x7f) << shift;
                        if ((b & 0x80) == 0) {
                            break;
                        }
                        shift += 7;
                        if (shift > 63) {
                            return out + "+badLen";
                        }
                    }
                    if (len < 0 || i + len > payload.length) {
                        return out + "+badLenVal";
                    }
                    out.add(field + ":bytes(" + len + ")");
                    i += (int) len;
                } else if (wire == 5) {
                    if (i + 4 > payload.length) {
                        return out + "+badI32";
                    }
                    out.add(field + ":i32");
                    i += 4;
                } else if (wire == 1) {
                    if (i + 8 > payload.length) {
                        return out + "+badI64";
                    }
                    out.add(field + ":i64");
                    i += 8;
                } else {
                    out.add(field + ":wire" + wire);
                    break;
                }
            }
        } catch (Exception e) {
            return out + "+ex:" + e.getClass().getSimpleName();
        }
        return out.toString();
    }
}
