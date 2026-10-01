package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import java.util.List;
import java.util.Objects;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "killall",
        permission = "server.killall",
        permissionTargeted = "server.killall.others")
public final class KillAllCommand implements PicocliCommandHandler {

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "killall")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<key>")
        private String key;

        @Parameters(index = "1", arity = "0..1", paramLabel = "[sceneId]")
        private Integer sceneId;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!Objects.equals(key, Configuration.HTTP_ENCRYPTION.keystorePassword)) {
                CommandHandler.sendMessage(sender != null ? sender : targetPlayer, "Wrong key");
                return;
            }

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
            toKill.forEach(entity -> scene.killEntity(entity, 0));
            CommandHandler.sendMessage(
                    sender,
                    translate(
                            sender,
                            "commands.killall.kill_monsters_in_scene",
                            toKill.size(),
                            scene.getId()));
        }
    }
}
