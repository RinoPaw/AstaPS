package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.database.PlayerCloneService;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

/** Console command for creating a playable snapshot of one offline player's save. */
@Command(label = "clone", targetRequirement = Command.TargetRequirement.NONE)
public final class CloneCommand implements PicocliCommandHandler {
    private record UidArg(int value) {}

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Clone(sender));
        commandLine.setExpandAtFiles(false);
        commandLine.registerConverter(UidArg.class, CloneCommand::parseUid);
        commandLine.setParameterExceptionHandler(
                (exception, argv) -> {
                    this.sendUsageMessage(sender);
                    return 2;
                });
        return commandLine;
    }

    private static UidArg parseUid(String raw) {
        String value = raw;
        if (value.regionMatches(true, 0, "UID", 0, 3)) {
            value = value.substring(3);
        }

        try {
            int uid = Integer.parseInt(value);
            if (uid <= 0) {
                throw new NumberFormatException();
            }
            return new UidArg(uid);
        } catch (NumberFormatException ignored) {
            throw new CommandLine.TypeConversionException(
                    "UID must be a positive integer, optionally prefixed with UID");
        }
    }

    @CommandLine.Command(name = "clone")
    private final class Clone implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<source-account>")
        private String sourceUsername;

        @Parameters(index = "1", paramLabel = "<target-account>")
        private String targetUsername;

        @Parameters(index = "2", arity = "0..1", paramLabel = "<UID>")
        private UidArg uid;

        private Clone(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            if (sender != null) {
                CommandHandler.sendTranslatedMessage(
                        sender, "commands.generic.console_execute_error");
                return;
            }

            try {
                var result =
                        PlayerCloneService.cloneOffline(
                                sourceUsername, targetUsername, uid == null ? 0 : uid.value());
                CommandHandler.sendMessage(
                        sender,
                        "Cloned %s (UID %d) to %s (UID %d): %d persisted documents copied."
                                .formatted(
                                        sourceUsername,
                                        result.sourceUid(),
                                        targetUsername,
                                        result.targetUid(),
                                        result.clonedDocuments()));
                CommandHandler.sendMessage(
                        sender,
                        "Friendships and public music-game beatmaps were intentionally not cloned.");
            } catch (IllegalArgumentException | IllegalStateException failure) {
                CommandHandler.sendMessage(sender, "Clone failed: " + failure.getMessage());
            }
        }
    }
}
