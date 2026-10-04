package emu.grasscutter.command.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "coop",
        permission = "server.coop",
        permissionTargeted = "server.coop.others",
        inlineTarget = false)
public final class CoopCommand implements CommandHandler {
    private record UidArg(int value) {}

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Args(sender, targetPlayer));
        commandLine.registerConverter(
                UidArg.class,
                value -> {
                    if (value == null || value.length() < 2 || value.charAt(0) != '@') {
                        throw new CommandLine.TypeConversionException("UID must use @<digits> syntax.");
                    }
                    try {
                        int uid = Integer.parseInt(value.substring(1));
                        if (uid <= 0) throw new NumberFormatException();
                        return new UidArg(uid);
                    } catch (NumberFormatException ignored) {
                        throw new CommandLine.TypeConversionException("UID must use @<digits> syntax.");
                    }
                });
        return commandLine;
    }

    @CommandLine.Command(name = "coop")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[@hostUID]")
        private UidArg hostUid;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            Player host;
            if (hostUid == null) {
                if (sender == null) {
                    CommandOutput.sendMessage(null, "A host UID is required from the console.");
                    return;
                }
                host = sender;
            } else {
                host = Grasscutter.getGameServer().getPlayerByUid(hostUid.value());
                if (host == null) {
                    CommandOutput.sendTranslatedMessage(sender, "commands.execution.player_offline_error");
                    return;
                }
            }

            if (targetPlayer.isInMultiplayer()) {
                targetPlayer.getServer().getMultiplayerSystem().leaveCoop(targetPlayer);
            }
            host.getServer().getMultiplayerSystem().applyEnterMp(targetPlayer, host.getUid());
            targetPlayer
                    .getServer()
                    .getMultiplayerSystem()
                    .applyEnterMpReply(host, targetPlayer.getUid(), true);
            CommandOutput.sendTranslatedMessage(
                    sender, "commands.coop.success", targetPlayer.getNickname(), host.getNickname());
        }
    }
}
