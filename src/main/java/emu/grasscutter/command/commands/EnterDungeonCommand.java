package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "enter_dungeon",
        aliases = {"enterdungeon", "dungeon"},
        permission = "player.enterdungeon",
        permissionTargeted = "player.enterdungeon.others")
public final class EnterDungeonCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "enter_dungeon")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<dungeonId>")
        private int dungeonId;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (dungeonId == targetPlayer.getSceneId()) {
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.enter_dungeon.in_dungeon_error"));
                return;
            }

            boolean entered =
                    targetPlayer
                            .getServer()
                            .getDungeonSystem()
                            .enterDungeon(targetPlayer.getSession().getPlayer(), 0, dungeonId, true);
            if (entered) {
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.enter_dungeon.changed", dungeonId));
            } else {
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.enter_dungeon.not_found_error"));
            }
        }
    }
}
