package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(label = "kill", targetRequirement = Command.TargetRequirement.PLAYER)
public final class KillCommand implements PicocliCommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("all", new KillAll(sender, targetPlayer));
        commandLine.addSubcommand("character", new KillCharacter(sender, targetPlayer));
        return commandLine;
    }

    @CommandLine.Command(name = "kill")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            KillCommand.this.sendUsageMessage(sender);
        }
    }

    @CommandLine.Command(name = "all")
    private static final class KillAll implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[sceneId]")
        private Integer sceneId;

        private KillAll(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!hasPermission(sender, targetPlayer, "server.killall", "server.killall.others")) return;

            Scene scene =
                    sceneId == null
                            ? targetPlayer.getScene()
                            : targetPlayer.getWorld().getSceneById(sceneId);
            if (scene == null) {
                CommandHandler.sendMessage(
                        sender, translate(sender, "commands.killall.scene_not_found_in_player_world"));
                return;
            }

            List<GameEntity> toKill =
                    scene.getEntities().values().stream()
                            .filter(EntityMonster.class::isInstance)
                            .toList();
            toKill.forEach(KillCommand::killEntity);
            CommandHandler.sendMessage(
                    sender,
                    translate(
                            sender,
                            "commands.killall.kill_monsters_in_scene",
                            toKill.size(),
                            scene.getId()));
        }
    }

    @CommandLine.Command(name = "character", aliases = {"avatar", "suicide"})
    private static final class KillCharacter implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        private KillCharacter(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!hasPermission(
                    sender,
                    targetPlayer,
                    "player.killcharacter",
                    "player.killcharacter.others")) return;

            EntityAvatar entity = targetPlayer.getTeamManager().getCurrentAvatarEntity();
            if (entity == null) {
                CommandHandler.sendMessage(sender, "No active character.");
                return;
            }
            killEntity(entity);

            CommandHandler.sendMessage(
                    sender, translate(sender, "commands.killCharacter.success", targetPlayer.getNickname()));
        }
    }

    private static void killEntity(GameEntity entity) {
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0f);
        boolean diedNow = entity.checkIfDead();
        entity.getWorld()
                .broadcastPacket(
                        new PacketEntityFightPropUpdateNotify(entity, FightProperty.FIGHT_PROP_CUR_HP));
        if (diedNow) {
            entity.getScene().killEntity(entity, 0);
        }
    }

    private static boolean hasPermission(
            Player sender, Player targetPlayer, String permission, String permissionTargeted) {
        if (sender == null) return true;
        var account = sender.getAccount();
        String required = targetPlayer != sender ? permissionTargeted : permission;
        if (account != null && account.hasPermission(required)) return true;
        CommandHandler.sendTranslatedMessage(sender, "commands.generic.permission_error");
        return false;
    }
}
