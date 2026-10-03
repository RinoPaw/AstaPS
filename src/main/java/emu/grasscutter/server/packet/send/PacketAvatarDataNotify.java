package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarDataNotifyOuterClass.AvatarDataNotify;

public class PacketAvatarDataNotify extends BasePacket {

    public PacketAvatarDataNotify(Player player) {
        super(PacketOpcodes.AvatarDataNotify, true);

        AvatarDataNotify.Builder proto =
                AvatarDataNotify.newBuilder()
                        .setCurAvatarTeamId(player.getTeamManager().getCurrentTeamId())
                        .addAllOwnedFlycloakList(player.getFlyCloakList())
                        .addAllOwnedCostumeList(player.getCostumeList())
                        .addAllOwnedTraceEffectList(player.getTraceEffectList());

        player.getAvatars().forEach(avatar -> proto.addAvatarList(avatar.toProto()));

        player
                .getTeamManager()
                .getTeams()
                .forEach(
                        (id, teamInfo) -> {
                            proto.putAvatarTeamMap(id, teamInfo.toProto(player));
                            if (id > 4) {
                                proto.addBackupAvatarTeamOrderList(id);
                            }
                        });

        // This packet is also sent before the first World/Scene exists during the native 7.1 intro.
        // Asking TeamManager for the current entity at that point used to manufacture a null entry in
        // activeTeam. The stored main avatar already carries the authoritative guid and this packet
        // historically overwrote chooseAvatarGuid with it below anyway, so use it directly.
        Avatar mainCharacter = player.getAvatars().getAvatarById(player.getMainCharacterId());
        if (mainCharacter != null) {
            proto.setChooseAvatarGuid(mainCharacter.getGuid());
        }

        this.setData(proto.build());
    }
}
