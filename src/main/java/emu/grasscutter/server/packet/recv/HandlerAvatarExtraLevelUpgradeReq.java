package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import emu.grasscutter.game.avatar.AvatarExtraLevelHelper;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAvatarExtraLevelUpgradeRsp;

/** Handles the current 7.1 AvatarExtraLevelUpgradeReq (CmdId 7915). */
@Opcodes(7915)
public final class HandlerAvatarExtraLevelUpgradeReq extends PacketHandler {
    // 7.1 _AvatarExtraLevelUpgradeReq: uint64 avatar_guid = 12.
    private static final int AVATAR_GUID_FIELD = 12;

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        if (player == null || payload == null || payload.length == 0) {
            return;
        }

        long avatarGuid = readAvatarGuid(payload);
        var avatar = player.getAvatars().getAvatarByGuid(avatarGuid);
        if (avatar == null) {
            session.send(new PacketAvatarExtraLevelUpgradeRsp(avatarGuid, 0, 0, 1));
            return;
        }

        int oldLevel = avatar.getLevel();
        boolean handled = AvatarExtraLevelHelper.upgradeAvatar(player, avatar);
        if (!handled) {
            session.send(new PacketAvatarExtraLevelUpgradeRsp(avatarGuid, oldLevel, oldLevel, 1));
            return;
        }

        int currentLevel = avatar.getLevel();
        // The helper already sends the failure response when payment fails. On a successful
        // request it deliberately leaves the dedicated 7.1 response to this handler.
        if (currentLevel != oldLevel) {
            session.send(new PacketAvatarExtraLevelUpgradeRsp(avatarGuid, oldLevel, currentLevel));
        }
    }

    private static long readAvatarGuid(byte[] payload) throws Exception {
        CodedInputStream input = CodedInputStream.newInstance(payload);
        long avatarGuid = 0L;

        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) {
                break;
            }

            if (WireFormat.getTagFieldNumber(tag) == AVATAR_GUID_FIELD
                    && WireFormat.getTagWireType(tag) == WireFormat.WIRETYPE_VARINT) {
                avatarGuid = input.readUInt64();
            } else if (!input.skipField(tag)) {
                break;
            }
        }

        return avatarGuid;
    }
}
