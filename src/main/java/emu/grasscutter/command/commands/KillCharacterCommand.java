package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.props.LifeState;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketLifeStateChangeNotify;
import picocli.CommandLine;

@Command(
        label = "killCharacter",
        aliases = {"suicide", "kill"},
        permission = "player.killcharacter",
        permissionTargeted = "player.killcharacter.others")
public final class KillCharacterCommand implements PicocliCommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "killCharacter")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            EntityAvatar entity = targetPlayer.getTeamManager().getCurrentAvatarEntity();
            entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0f);
            entity.getWorld()
                    .broadcastPacket(
                            new PacketEntityFightPropUpdateNotify(entity, FightProperty.FIGHT_PROP_CUR_HP));
            entity.getWorld().broadcastPacket(new PacketLifeStateChangeNotify(0, entity, LifeState.LIFE_DEAD));
            targetPlayer.getScene().removeEntity(entity);
            entity.onDeath(0);

            CommandHandler.sendMessage(
                    sender, translate(sender, "commands.killCharacter.success", targetPlayer.getNickname()));
        }
    }
}
