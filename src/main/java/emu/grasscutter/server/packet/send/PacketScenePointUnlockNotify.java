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
     * Relock points and hide them again.
     *
     * <p>The current generated 7.1 API misnames wire field 4 as {@code unhidePointList}; the
     * official client treats field 4 as {@code locked_point_list}. Field 6 is {@code
     * hide_point_list}.
     */
    public static PacketScenePointUnlockNotify lock(int sceneId, int pointId) {
        return lock(sceneId, java.util.List.of(pointId));
    }

    public static PacketScenePointUnlockNotify lock(int sceneId, Iterable<Integer> pointIds) {
        PacketScenePointUnlockNotify packet = new PacketScenePointUnlockNotify();

        ScenePointUnlockNotify.Builder p =
                ScenePointUnlockNotify.newBuilder()
                        .setSceneId(sceneId)
                        .addAllUnhidePointList(pointIds)
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
