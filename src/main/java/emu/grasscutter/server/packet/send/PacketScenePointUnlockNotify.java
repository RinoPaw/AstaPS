package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ScenePointUnlockNotifyOuterClass.ScenePointUnlockNotify;

public class PacketScenePointUnlockNotify extends BasePacket {
    public PacketScenePointUnlockNotify(int sceneId, int pointId) {
        super(PacketOpcodes.ScenePointUnlockNotify);

        // In the official 7.1 client, wire field 4 is locked_point_list even though the current
        // generated API names it unhidePointList. A normal unlock only needs scene_id + point_list;
        // sending field 4 here makes the same point appear in both unlock and lock lists.
        ScenePointUnlockNotify.Builder p =
                ScenePointUnlockNotify.newBuilder()
                        .setSceneId(sceneId)
                        .addPointList(pointId);

        this.setData(p);
    }

    /**
     * The mirror of the unlock constructors: relock a point and hide it again.
     *
     * <p>Static rather than a constructor because {@code (int, int)} is already taken by the unlock
     * above, and the two differ only in which repeated fields they fill - {@code locked_point_list}
     * / {@code hide_point_list} here against {@code point_list} / {@code unhide_point_list} there.
     */
    public static PacketScenePointUnlockNotify lock(int sceneId, int pointId) {
        return lock(sceneId, java.util.List.of(pointId));
    }

    public static PacketScenePointUnlockNotify lock(int sceneId, Iterable<Integer> pointIds) {
        PacketScenePointUnlockNotify packet = new PacketScenePointUnlockNotify();

        ScenePointUnlockNotify.Builder p =
                ScenePointUnlockNotify.newBuilder()
                        .setSceneId(sceneId)
                        .addAllHidePointList(pointIds)
                        .addAllHidePointList(pointIds);

        packet.setData(p);
        return packet;
    }

    private PacketScenePointUnlockNotify() {
        super(PacketOpcodes.ScenePointUnlockNotify);
    }

    public PacketScenePointUnlockNotify(int sceneId, Iterable<Integer> pointIds) {
        super(PacketOpcodes.ScenePointUnlockNotify);

        ScenePointUnlockNotify.Builder p =
                ScenePointUnlockNotify.newBuilder()
                        .setSceneId(sceneId)
                        .addAllPointList(pointIds);

        this.setData(p);
    }
}
