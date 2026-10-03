package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.server.packet.send.PacketStoreItemChangeNotify;
import java.util.*;

/** Third-person shooter weapons: hand them out, wear them on the current avatar, refill ammo. */
@Command(
        label = "tps",
        usage = {
            "give [<weaponId>]",
            "accessory",
            "wear [<weaponId>...]",
            "refill",
            "ammo [type slot|one] [current reserve|limit|<n>] [notify] [supply]"
        },
        permission = "player.tps",
        permissionTargeted = "player.tps.others")
public final class TpsCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.isEmpty()) {
            sendUsageMessage(sender);
            return;
        }

        var rest = args.subList(1, args.size());
        switch (args.get(0).toLowerCase()) {
            case "give" -> this.give(sender, targetPlayer, rest);
            case "accessory" -> this.unlockAccessories(sender, targetPlayer);
            case "wear" -> this.wear(sender, targetPlayer, rest);
            case "ammo" -> this.ammo(sender, targetPlayer, rest);
            case "refill" -> {
                TpsWeaponSystem.refillAmmunition(targetPlayer);
                CommandHandler.sendMessage(sender, "TPS ammunition refilled.");
            }
            default -> sendUsageMessage(sender);
        }
    }

    private void give(Player sender, Player target, List<String> args) {
        var ids = new ArrayList<Integer>();
        if (args.isEmpty()) {
            ids.addAll(GameData.getTpsWeaponDataMap().keySet());
        } else {
            var id = parseWeaponId(sender, args.get(0));
            if (id == null) return;
            ids.add(id);
        }
        ids.forEach(target.getInventory()::addItem);
        CommandHandler.sendMessage(sender, "Gave TPS weapons " + ids + ".");
    }

    private void unlockAccessories(Player sender, Player target) {
        var changed = new ArrayList<GameItem>();
        for (var accessory : GameData.getTpsWeaponAccessoryDataMap().values()) {
            var item = TpsWeaponSystem.findOwnedWeapon(target, accessory.getTpsWeaponId());
            if (item == null || item.getTpsAccessoryIds().contains(accessory.getId())) continue;
            item.getTpsAccessoryIds().add(accessory.getId());
            if (!changed.contains(item)) changed.add(item);
        }
        changed.forEach(GameItem::save);
        if (!changed.isEmpty()) target.sendPacket(new PacketStoreItemChangeNotify(changed));
        // Accessory affixes add abilities, so the wearers of changed weapons need them rebuilt.
        var changedIds = changed.stream().map(GameItem::getItemId).toList();
        for (var avatar : TpsWeaponSystem.getTpsWearers(target)) {
            if (avatar.getTpsWeaponIds().stream().noneMatch(changedIds::contains)) continue;
            avatar.recalcStats();
            TpsWeaponSystem.sendEquipChange(avatar);
        }
        CommandHandler.sendMessage(sender, "Unlocked accessories on " + changed.size() + " TPS weapons.");
    }

    private void wear(Player sender, Player target, List<String> args) {
        var entity = target.getTeamManager().getCurrentAvatarEntity();
        if (entity == null) {
            CommandHandler.sendMessage(sender, "No avatar on the field.");
            return;
        }

        var guids = new ArrayList<Long>();
        for (String arg : args) {
            var id = parseWeaponId(sender, arg);
            if (id == null) return;
            var item = TpsWeaponSystem.findOwnedWeapon(target, id);
            if (item == null) {
                CommandHandler.sendMessage(sender, "TPS weapon " + id + " is not owned; /tps give first.");
                return;
            }
            guids.add(item.getGuid());
        }

        int retcode = TpsWeaponSystem.wear(target, entity.getAvatar().getGuid(), guids);
        CommandHandler.sendMessage(
                sender, retcode == 0 ? "TPS weapons now worn: " + args : "Wear failed, retcode " + retcode + ".");
    }

    /**
     * Ammunition experiments: what the client reads from the weapons' ammunition list is not
     * settled, so the fill can be switched in game and the server-to-client pushes tried by hand.
     */
    private void ammo(Player sender, Player target, List<String> args) {
        boolean resend = false;
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i).toLowerCase();
            String next = i + 1 < args.size() ? args.get(i + 1).toLowerCase() : "";
            switch (arg) {
                case "type" -> {
                    TpsWeaponSystem.ammoTypeIsSlot = !next.equals("one");
                    resend = true;
                    i++;
                }
                case "current" -> {
                    switch (next) {
                        case "reserve" -> TpsWeaponSystem.ammoCurrent = TpsWeaponSystem.AmmoCurrent.RESERVE;
                        case "limit" -> TpsWeaponSystem.ammoCurrent = TpsWeaponSystem.AmmoCurrent.LIMIT;
                        default -> {
                            try {
                                TpsWeaponSystem.ammoFixed = Integer.parseInt(next);
                                TpsWeaponSystem.ammoCurrent = TpsWeaponSystem.AmmoCurrent.FIXED;
                            } catch (NumberFormatException e) {
                                sendUsageMessage(sender);
                                return;
                            }
                        }
                    }
                    resend = true;
                    i++;
                }
                case "notify" ->
                        CommandHandler.sendMessage(
                                sender, "Sent 24371 with " + TpsWeaponSystem.sendAmmunitionNotify(target) + " entries.");
                case "supply" ->
                        CommandHandler.sendMessage(
                                sender, "Sent SUPPLY to " + TpsWeaponSystem.sendAmmunitionSupply(target) + " avatars.");
                default -> {
                    sendUsageMessage(sender);
                    return;
                }
            }
        }
        if (resend) TpsWeaponSystem.getTpsWearers(target).forEach(TpsWeaponSystem::sendEquipChange);
        CommandHandler.sendMessage(
                sender,
                "TPS ammo list: type="
                        + (TpsWeaponSystem.ammoTypeIsSlot ? "slot" : "one")
                        + " current="
                        + TpsWeaponSystem.ammoCurrent
                        + (TpsWeaponSystem.ammoCurrent == TpsWeaponSystem.AmmoCurrent.FIXED
                                ? "(" + TpsWeaponSystem.ammoFixed + ")"
                                : ""));
    }

    private static Integer parseWeaponId(Player sender, String arg) {
        try {
            int id = Integer.parseInt(arg);
            if (GameData.getTpsWeaponDataMap().containsKey(id)) return id;
        } catch (NumberFormatException ignored) {
        }
        CommandHandler.sendMessage(
                sender, "Unknown TPS weapon " + arg + ". Known: " + GameData.getTpsWeaponDataMap().keySet());
        return null;
    }
}
