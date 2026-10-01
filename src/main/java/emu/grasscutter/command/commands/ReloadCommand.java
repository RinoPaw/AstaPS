package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.game.combine.CombineManger;
import emu.grasscutter.game.player.Player;
import java.util.Objects;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "reload",
        permission = "server.reload",
        targetRequirement = Command.TargetRequirement.NONE)
public final class ReloadCommand implements PicocliCommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender));
    }

    @CommandLine.Command(name = "reload")
    private static final class Args implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<key>")
        private String key;

        private Args(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            if (!Objects.equals(key, Configuration.HTTP_ENCRYPTION.keystorePassword)) {
                CommandHandler.sendMessage(sender, "Wrong key");
                return;
            }

            CommandHandler.sendMessage(sender, translate(sender, "commands.reload.reload_start"));
            Grasscutter.loadConfig();
            Grasscutter.loadLanguage();
            Grasscutter.getGameServer().getGachaSystem().load();
            Grasscutter.getGameServer().getShopSystem().load();
            CombineManger.initialize();
            CommandHandler.sendMessage(sender, translate(sender, "commands.reload.reload_done"));
        }
    }
}
