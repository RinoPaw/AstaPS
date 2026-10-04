package emu.grasscutter.command.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.command.Command.TargetRequirement;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "sendMessage",
        aliases = {"say", "sendservmsg", "sendservermessage", "b", "broadcast"},
        permission = "server.sendmessage",
        permissionTargeted = "server.sendmessage.others",
        targetRequirement = TargetRequirement.NONE)
public final class SendMessageCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "sendMessage")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0..*", arity = "1..*", paramLabel = "<message>")
        private String[] words;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            String message = String.join(" ", words);
            if (targetPlayer == null) {
                for (Player player : Grasscutter.getGameServer().getPlayers().values()) {
                    CommandOutput.sendMessage(player, message);
                }
            } else {
                CommandOutput.sendMessage(targetPlayer, message);
            }
            CommandOutput.sendTranslatedMessage(sender, "commands.sendMessage.success");
        }
    }
}
