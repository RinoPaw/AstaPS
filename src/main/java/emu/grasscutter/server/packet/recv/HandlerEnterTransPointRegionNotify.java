package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
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
        var player = session.getPlayer();
        int uid = player != null ? player.getUid() : 0;

        try {
            EnterTransPointRegionNotify notify = EnterTransPointRegionNotify.parseFrom(payload);
            int sceneId = notify.getSceneId();
            int pointId = notify.getPointId();

            var entry = GameData.getScenePointEntryById(sceneId, pointId);
            boolean isStatue =
                    entry != null
                            && emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(
                                    entry.getPointData());
            boolean unlocked =
                    player != null
                            && sceneId > 0
                            && pointId > 0
                            && player.getUnlockedScenePoints(sceneId).contains(pointId);
            boolean forceLocked =
                    player != null
                            && sceneId > 0
                            && pointId > 0
                            && player.isScenePointForceLocked(sceneId, pointId);

            Grasscutter.getLogger()
                    .info(
                            "[statue-probe] EnterTransPoint uid={} scene={} point={} statue={} unlocked={} forceLocked={} hex={} tags={}",
                            uid,
                            sceneId,
                            pointId,
                            isStatue,
                            unlocked,
                            forceLocked,
                            hex,
                            tags);

            // Probe only: do not unlock a locked statue from EnterTransPointRegionNotify and do not
            // resend ScenePointUnlockNotify/GetScenePointRsp/GetSceneAreaRsp here. If the stock 7.1
            // client owns a separate statue activation transaction, this lets it become visible.
            // SotSManager below still handles the actual enter-region behavior (revive/heal timer).
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .warn(
                            "[statue-probe] EnterTransPoint parse failed uid={} err={} hex={} tags={}",
                            uid,
                            e.toString(),
                            hex,
                            tags);
        }

        if (player != null) {
            player.getSotsManager().handleEnterTransPointRegionNotify();
        }
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
