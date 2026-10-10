package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.InventoryAddPolicy;
import emu.grasscutter.game.inventory.InventoryAddResult;
import emu.grasscutter.game.inventory.InventoryGrantBuilder;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.EquipParamOuterClass.EquipParam;
import emu.grasscutter.net.proto.GetMailItemRspOuterClass.GetMailItemRsp;
import java.util.*;

public class PacketGetMailItemRsp extends BasePacket {
    public PacketGetMailItemRsp(Player player, List<Integer> mailList) {
        super(PacketOpcodes.GetMailItemRsp);
        List<Mail> claimedMessages = new ArrayList<>();
        List<EquipParam> claimedItems = new ArrayList<>();
        var proto = GetMailItemRsp.newBuilder();

        synchronized (player) {
            for (int mailId : new LinkedHashSet<>(mailList)) {
                Mail message = player.getMail(mailId);
                if (message == null || message.isAttachmentGot
                        || message.itemList == null || message.itemList.isEmpty()) {
                    continue;
                }
                List<GameItem> items = new ArrayList<>();
                List<EquipParam> itemParams = new ArrayList<>();
                try {
                    for (Mail.MailItem attachment : message.itemList) {
                        if (attachment == null || attachment.itemCount <= 0) {
                            throw new IllegalArgumentException("Invalid mail attachment count");
                        }
                        var data = GameData.getItemDataMap().get(attachment.itemId);
                        items.addAll(InventoryGrantBuilder.create(
                                data, attachment.itemCount, attachment.itemLevel));
                        itemParams.add(EquipParam.newBuilder()
                                .setItemId(attachment.itemId)
                                .setItemNum(attachment.itemCount)
                                .setItemLevel(attachment.itemLevel)
                                .setPromoteLevel(GameItem.getMinPromoteLevel(attachment.itemLevel))
                                .build());
                    }
                } catch (RuntimeException exception) {
                    Grasscutter.getLogger().warn(
                            "Mail attachment preparation rejected uid={} mail={}",
                            player.getUid(), mailId, exception);
                    continue;
                }

                try {
                    InventoryAddResult result = player.getInventory().addItems(
                            items,
                            ActionReason.MailAttachment,
                            InventoryAddPolicy.ALL_OR_NOTHING,
                            () -> !message.isAttachmentGot,
                            () -> {
                                // Mark before writes so an interrupted grant cannot be replayed.
                                message.isAttachmentGot = true;
                                player.replaceMailByIndex(mailId, message);
                            });
                    if (!result.allAccepted()) {
                        Grasscutter.getLogger().warn(
                                "Mail claim rejected uid={} mail={} result={}",
                                player.getUid(), mailId, result.entries());
                        continue;
                    }
                    claimedMessages.add(message);
                    claimedItems.addAll(itemParams);
                } catch (RuntimeException exception) {
                    Grasscutter.getLogger().error(
                            "Mail claim interrupted uid={} mail={}",
                            player.getUid(), mailId, exception);
                }
            }
            if (!claimedMessages.isEmpty()) {
                player.save();
            }
        }

        for (Mail message : claimedMessages) {
            proto.addMailIdList(player.getMailHandler().toClientMailId(player.getMailId(message)));
        }
        proto.addAllItemList(claimedItems);
        setData(proto.build());

        if (!claimedMessages.isEmpty()) {
            player.getSession().send(new PacketMailChangeNotify(player, claimedMessages));
        }
    }
}
