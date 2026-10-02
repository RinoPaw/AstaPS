package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ScenePointUnlockNotifyOuterClass.ScenePointUnlockNotify;

public class PacketScenePointUnlockNotify extends BasePacket {
    public PacketScenePointUnlockNotify(int sceneId, int pointId) {
        super(PacketOpcodes.ScenePointUnlockNotify);

        ScenePointUnlockNotify.Builder p =
                ScenePointUnlockNotify.newBuilder().setSceneId(sceneId).addPointList(pointId);

        this.setData(p);
    }

    /**
     * Relock helper retained from the current branch. The generated 7.1 semantic aliases for the
     * remaining repeated fields are being audited separately from the unlock-response probe.
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
                ScenePointUnlockNotify.newBuilder().setSceneId(sceneId).addAllPointList(pointIds);

        this.setData(p);
    }
}
