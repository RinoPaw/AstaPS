package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketAvatarFetterDataNotify;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketAvatarPropNotify;
import emu.grasscutter.server.packet.send.PacketProudSkillChangeNotify;
import emu.grasscutter.server.packet.send.PacketStoreItemChangeNotify;
import java.util.ArrayList;
import picocli.CommandLine;

@Command(
        label = "max",
        aliases = {"maxavatar", "maxchar"},
        permission = "player.max",
        permissionTargeted = "player.max.others")
public final class MaxCommand implements CommandHandler {
    private static final int MAX_AVATAR_LEVEL = 90;
    private static final int MAX_CONSTELLATION = 6;
    private static final int MAX_FETTER_LEVEL = 10;
    private static final int MAX_REFINEMENT = 4;
    private static final int DEFAULT_MAX_TALENT = 10;

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Max(sender, targetPlayer, false));
        commandLine.addSubcommand("all", new Max(sender, targetPlayer, true));
        return commandLine;
    }

    @CommandLine.Command(name = "max")
    private final class Max implements Runnable {
        private final Player sender;
        private final Player targetPlayer;
        private final boolean all;

        private Max(Player sender, Player targetPlayer, boolean all) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
            this.all = all;
        }

        @Override
        public void run() {
            var targets = new ArrayList<Avatar>();
            if (all) {
                targetPlayer.getAvatars().forEach(targets::add);
            } else {
                var entity = targetPlayer.getTeamManager().getCurrentAvatarEntity();
                if (entity == null) {
                    CommandOutput.sendMessage(sender, "No character is currently active.");
                    return;
                }
                targets.add(entity.getAvatar());
            }

            if (targets.isEmpty()) {
                CommandOutput.sendMessage(sender, "No characters to max out.");
                return;
            }

            targets.forEach(avatar -> maxAvatar(targetPlayer, avatar));
            healActiveTeam(targetPlayer);

            if (all) {
                CommandOutput.sendMessage(sender, "Maxed out " + targets.size() + " characters.");
            } else {
                CommandOutput.sendMessage(
                        sender, "Maxed out " + targets.get(0).getAvatarData().getName() + ".");
            }
        }
    }

    private void maxAvatar(Player player, Avatar avatar) {
        avatar.setLevel(MAX_AVATAR_LEVEL);
        avatar.setPromoteLevel(Avatar.getMinPromoteLevel(MAX_AVATAR_LEVEL));

        var depot = avatar.getSkillDepot();
        if (depot != null) {
            depot.getSkillsAndEnergySkill()
                    .forEach(
                            id -> {
                                var levels = GameData.getAvatarSkillLevels(id);
                                int max =
                                        levels == null || levels.isEmpty()
                                                ? DEFAULT_MAX_TALENT
                                                : levels.intStream().max().orElse(DEFAULT_MAX_TALENT);
                                avatar.setSkillLevel(id, max);
                            });
        }

        avatar.forceConstellationLevel(MAX_CONSTELLATION);
        avatar.recalcConstellations();
        avatar.setFetterLevel(MAX_FETTER_LEVEL);
        maxWeapon(player, avatar);
        avatar.recalcStats(true);
        avatar.save();

        player.sendPacket(new PacketAvatarPropNotify(avatar));
        player.sendPacket(new PacketProudSkillChangeNotify(avatar));
        player.sendPacket(new PacketAvatarFetterDataNotify(avatar));
    }

    private void maxWeapon(Player player, Avatar avatar) {
        GameItem weapon = avatar.getWeapon();
        if (weapon == null || weapon.getItemData() == null) {
            return;
        }

        int promoteId = weapon.getItemData().getWeaponPromoteId();
        int maxPromoteLevel = weapon.getPromoteLevel();
        int maxLevel = weapon.getLevel();
        for (int promote = 0; ; promote++) {
            var data = GameData.getWeaponPromoteData(promoteId, promote);
            if (data == null) {
                break;
            }
            maxPromoteLevel = promote;
            maxLevel = Math.max(maxLevel, data.getUnlockMaxLevel());
        }

        weapon.setPromoteLevel(maxPromoteLevel);
        weapon.setLevel(maxLevel);
        var affixes = weapon.getItemData().getSkillAffix();
        if (affixes != null && affixes.length > 0 && affixes[0] != 0) {
            weapon.setRefinement(MAX_REFINEMENT);
        }

        weapon.save();
        player.sendPacket(new PacketStoreItemChangeNotify(weapon));
    }

    private void healActiveTeam(Player player) {
        player.getTeamManager()
                .getActiveTeam()
                .forEach(
                        entity -> {
                            if (entity.isAlive()) {
                                entity.setFightProperty(
                                        FightProperty.FIGHT_PROP_CUR_HP,
                                        entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
                                entity.getWorld()
                                        .broadcastPacket(
                                                new PacketAvatarFightPropUpdateNotify(
                                                        entity.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP));
                            } else {
                                entity.reviveToRatio(1f);
                            }
                        });
    }
}
