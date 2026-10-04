package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import at.favre.lib.crypto.bcrypt.BCrypt;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Unmatched;

@Command(
        label = "account",
        targetRequirement = Command.TargetRequirement.NONE,
        inlineTarget = false)
public final class AccountCommand implements PicocliCommandHandler {
    private record UidArg(int value) {}

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        if (sender != null) {
            var rejected = new CommandLine(new ConsoleOnly(sender));
            rejected.setUnmatchedArgumentsAllowed(true);
            rejected.setExpandAtFiles(false);
            return rejected;
        }

        var commandLine = new CommandLine(new AccountRoot(sender));
        commandLine.setExpandAtFiles(false);
        commandLine.registerConverter(UidArg.class, value -> parseUid(sender, value));

        commandLine.addSubcommand("create", new CreateWithPassword(sender));
        commandLine.addSubcommand("clone", new Clone(sender));
        commandLine.addSubcommand("delete", new Delete(sender));
        commandLine.addSubcommand("resetpass", new ResetPass(sender));
        commandLine.addSubcommand("list", new ListAccounts(sender));

        commandLine.setParameterExceptionHandler(
                (exception, argv) -> {
                    if (exception.getCause() instanceof CommandLine.TypeConversionException cause) {
                        CommandHandler.sendMessage(sender, cause.getMessage());
                    } else {
                        this.sendUsageMessage(sender);
                    }
                    return 2;
                });
        return commandLine;
    }

    private static UidArg parseUid(Player sender, String value) {
        if (value == null || value.length() < 2 || value.charAt(0) != '@') {
            throw new CommandLine.TypeConversionException("UID must use @<digits> syntax.");
        }
        try {
            int uid = Integer.parseInt(value.substring(1));
            if (uid <= 0) throw new NumberFormatException();
            return new UidArg(uid);
        } catch (NumberFormatException ignored) {
            throw new CommandLine.TypeConversionException(
                    translate(sender, "commands.account.invalid"));
        }
    }

    @picocli.CommandLine.Command(name = "account")
    private static final class ConsoleOnly implements Runnable {
        private final Player sender;

        @Unmatched private String[] ignored;

        private ConsoleOnly(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            CommandHandler.sendTranslatedMessage(sender, "commands.generic.console_execute_error");
        }
    }

    @picocli.CommandLine.Command(name = "account")
    private final class AccountRoot implements Runnable {
        private final Player sender;

        private AccountRoot(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            AccountCommand.this.sendUsageMessage(sender);
        }
    }

    @picocli.CommandLine.Command(name = "create")
    private final class CreateWithPassword implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<username>")
        private String username;

        @Parameters(index = "1", paramLabel = "<password>")
        private String password;

        @Parameters(index = "2", arity = "0..1", paramLabel = "[@UID]")
        private UidArg uid;

        private CreateWithPassword(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            createAccount(sender, username, password, uid == null ? 0 : uid.value());
        }
    }

    @picocli.CommandLine.Command(name = "clone")
    private static final class Clone implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<source-account>")
        private String sourceUsername;

        @Parameters(index = "1", paramLabel = "<target-account>")
        private String targetUsername;

        @Parameters(index = "2", arity = "0..1", paramLabel = "[@UID]")
        private UidArg uid;

        private Clone(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
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

    @picocli.CommandLine.Command(name = "delete")
    private final class Delete implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<username>")
        private String username;

        private Delete(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            Account toDelete = DatabaseHelper.getAccountByName(username);
            if (toDelete == null) {
                CommandHandler.sendMessage(sender, translate(sender, "commands.account.no_account"));
                return;
            }

            AccountDeletionService.delete(toDelete);
            CommandHandler.sendMessage(sender, translate(sender, "commands.account.delete"));
        }
    }

    @picocli.CommandLine.Command(name = "resetpass")
    private final class ResetPass implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<username>")
        private String username;

        @Parameters(index = "1", paramLabel = "<password>")
        private String password;

        private ResetPass(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            Account toUpdate = DatabaseHelper.getAccountByName(username);
            if (toUpdate == null) {
                CommandHandler.sendMessage(sender, translate(sender, "commands.account.no_account"));
                return;
            }

            String passwordHash = hashPassword(sender, password);
            if (passwordHash == null) return;

            kickAccount(toUpdate);
            toUpdate.setPassword(passwordHash);
            toUpdate.save();
            CommandHandler.sendMessage(sender, "Password Updated.");
        }
    }

    @picocli.CommandLine.Command(name = "list")
    private final class ListAccounts implements Runnable {
        private final Player sender;

        private ListAccounts(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            CommandHandler.sendMessage(sender, "Note: This command might take a while to complete.");
            CommandHandler.sendMessage(
                    sender,
                    "Accounts: \n"
                            + DatabaseManager.getAccountDatastore().find(Account.class).stream()
                                    .map(
                                            acc ->
                                                    "%s: %s (%s)"
                                                            .formatted(
                                                                    acc.getId(),
                                                                    acc.getUsername(),
                                                                    acc.getReservedPlayerUid() == 0
                                                                            ? getPlayerUid(acc)
                                                                            : acc.getReservedPlayerUid()))
                                    .collect(Collectors.joining("\n")));
        }
    }

    private void createAccount(Player sender, String username, String password, int uid) {
        String passwordHash = hashPassword(sender, password);
        if (passwordHash == null) return;

        Account account = DatabaseHelper.createAccountWithUid(username, uid);
        if (account == null) {
            CommandHandler.sendMessage(sender, translate(sender, "commands.account.exists"));
            return;
        }

        account.setPassword(passwordHash);
        account.addPermission("*");
        account.save();
        CommandHandler.sendMessage(
                sender, translate(sender, "commands.account.create", account.getReservedPlayerUid()));
    }

    private String hashPassword(Player sender, String password) {
        try {
            return BCrypt.withDefaults().hashToString(12, password.toCharArray());
        } catch (IllegalArgumentException invalidPassword) {
            CommandHandler.sendMessage(sender, "Invalid password.");
            return null;
        }
    }

    private String getPlayerUid(Account account) {
        var player = DatabaseHelper.getPlayerByAccount(account, Player.class);
        return player == null ? "no UID" : String.valueOf(player.getUid());
    }

    private void kickAccount(Account account) {
        Player player = Grasscutter.getGameServer().getPlayerByAccountId(account.getId());
        if (player != null) player.getSession().close();
    }
}
