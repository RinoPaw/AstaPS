package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropNotify;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketAvatarLifeStateChangeNotify;
import picocli.CommandLine;

@Command(
        label = "ER",
        aliases = {"e", "er", "energy"},
        permission = "player.setprop",
        permissionTargeted = "player.setprop.others")
public final class erCommand implements PicocliCommandHandler {
    private static final FightProperty[][] ENERGY_PROPERTIES = {
        {FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY, FightProperty.FIGHT_PROP_MAX_FIRE_ENERGY},
        {FightProperty.FIGHT_PROP_CUR_ELEC_ENERGY, FightProperty.FIGHT_PROP_MAX_ELEC_ENERGY},
        {FightProperty.FIGHT_PROP_CUR_WATER_ENERGY, FightProperty.FIGHT_PROP_MAX_WATER_ENERGY},
        {FightProperty.FIGHT_PROP_CUR_GRASS_ENERGY, FightProperty.FIGHT_PROP_MAX_GRASS_ENERGY},
        {FightProperty.FIGHT_PROP_CUR_WIND_ENERGY, FightProperty.FIGHT_PROP_MAX_WIND_ENERGY},
        {FightProperty.FIGHT_PROP_CUR_ICE_ENERGY, FightProperty.FIGHT_PROP_MAX_ICE_ENERGY},
        {FightProperty.FIGHT_PROP_CUR_ROCK_ENERGY, FightProperty.FIGHT_PROP_MAX_ROCK_ENERGY}
    };

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "ER")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            targetPlayer
                    .getTeamManager()
                    .getActiveTeam()
                    .forEach(
                            entity -> {
                                boolean alive = entity.isAlive();
                                for (FightProperty[] pair : ENERGY_PROPERTIES) {
                                    entity.setFightProperty(pair[0], entity.getFightProperty(pair[1]));
                                    entity.getWorld()
                                            .broadcastPacket(
                                                    new PacketAvatarFightPropUpdateNotify(
                                                            entity.getAvatar(), pair[0]));
                                }

                                var avatar = entity.getAvatar();
                                avatar.setFightProperty(
                                        FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY,
                                        avatar.getFightProperty(
                                                FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY));
                                targetPlayer.sendPacket(new PacketAvatarFightPropNotify(avatar));
                                if (!alive) {
                                    entity.getWorld()
                                            .broadcastPacket(
                                                    new PacketAvatarLifeStateChangeNotify(
                                                            entity.getAvatar()));
                                }
                            });
            CommandHandler.sendMessage(sender, "Restored elemental energy successfully.");
        }
    }
}
