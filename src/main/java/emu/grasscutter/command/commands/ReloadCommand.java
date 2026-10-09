package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.combine.CombineManger;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;

@Command(
        label = "reload",
        permission = "server.reload",
        targetRequirement = Command.TargetRequirement.NONE)
public final class ReloadCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender));
    }

    @CommandLine.Command(name = "reload")
    private static final class Args implements Runnable {
        private final Player sender;

        private Args(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            CommandOutput.sendMessage(sender, translate(sender, "commands.reload.reload_start"));
            Grasscutter.loadConfig();
            Grasscutter.loadLanguage();
            Grasscutter.getGameServer().getGachaSystem().load();
            Grasscutter.getGameServer().getShopSystem().load();
            CombineManger.initialize();
            CommandOutput.sendMessage(sender, translate(sender, "commands.reload.reload_done"));
        }
    }
}
