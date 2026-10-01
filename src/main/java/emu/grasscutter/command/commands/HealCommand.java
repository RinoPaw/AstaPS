package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.ChangeHpDebtsReason._ChangeHpDebtsReason;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketAvatarLifeStateChangeNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import picocli.CommandLine;

@Command(
        label = "heal",
        aliases = {"h"},
        permission = "player.heal",
        permissionTargeted = "player.heal.others")
public final class HealCommand implements PicocliCommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "heal")
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
                                boolean wasAlive = entity.isAlive();
                                entity.setFightProperty(
                                        FightProperty.FIGHT_PROP_CUR_HP,
                                        entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));

                                if (entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS) > 0) {
                                    entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 0.0f);
                                    entity.getWorld()
                                            .broadcastPacket(
                                                    new PacketEntityFightPropUpdateNotify(
                                                            entity,
                                                            FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
                                    entity.getWorld()
                                            .broadcastPacket(
                                                    new PacketEntityFightPropChangeReasonNotify(
                                                            entity,
                                                            FightProperty.FIGHT_PROP_CUR_HP_DEBTS,
                                                            0f,
                                                            PropChangeReason.PropChangeReason_PROP_CHANGE_NONE,
                                                            _ChangeHpDebtsReason
                                                                    ._ChangeHpDebtsReason_CHANGE_HP_DEBTS_PAY_FINISH));
                                }

                                entity.getWorld()
                                        .broadcastPacket(
                                                new PacketAvatarFightPropUpdateNotify(
                                                        entity.getAvatar(),
                                                        FightProperty.FIGHT_PROP_CUR_HP));
                                if (!wasAlive) {
                                    entity.getWorld()
                                            .broadcastPacket(
                                                    new PacketAvatarLifeStateChangeNotify(
                                                            entity.getAvatar()));
                                }
                            });

            CommandHandler.sendMessage(sender, translate(sender, "commands.heal.success"));
        }
    }
}
