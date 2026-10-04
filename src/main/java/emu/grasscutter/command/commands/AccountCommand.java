package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import at.favre.lib.crypto.bcrypt.BCrypt;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import java.util.List;
import java.util.stream.Collectors;

@Command(
        label = "account",
        usage = {
            "create <username> [<password>] [<UID>]",
            "delete <username>",
            "resetpass <username> [password]"
        },
        targetRequirement = Command.TargetRequirement.NONE)
public final class AccountCommand implements CommandHandler {
    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (sender != null) {
            CommandHandler.sendTranslatedMessage(sender, "commands.generic.console_execute_error");
            return;
        }
        if (args.isEmpty()) {
            this.sendUsageMessage(sender);
            return;
        }

        String action = args.get(0);

        switch (action) {
            default -> this.sendUsageMessage(sender);
            case "create" -> {
                if (args.size() < 2 || args.size() > 4) {
                    this.sendUsageMessage(sender);
                    return;
                }
                var username = args.get(1);
                var password = args.size() >= 3 ? args.get(2) : null;

                int uid = 0;
                if (args.size() == 4) {
                    try {
                        uid = Integer.parseInt(args.get(3));
                    } catch (NumberFormatException ignored) {
                        CommandHandler.sendMessage(sender, translate(sender, "commands.account.invalid"));
                        return;
                    }
                }

                Account account = DatabaseHelper.createAccountWithUid(username, uid);
                if (account == null) {
                    CommandHandler.sendMessage(sender, translate(sender, "commands.account.exists"));
                    return;
                }

                if (password != null && !password.isEmpty()) {
                    account.setPassword(BCrypt.withDefaults().hashToString(12, password.toCharArray()));
                }
                account.addPermission("*");
                account.save(); // Save account to database.

                CommandHandler.sendMessage(
                        sender, translate(sender, "commands.account.create", account.getReservedPlayerUid()));
            }
            case "delete" -> {
                if (args.size() != 2) {
                    this.sendUsageMessage(sender);
                    return;
                }
                var username = args.get(1);

                // Get the account we want to delete.
                Account toDelete = DatabaseHelper.getAccountByName(username);
                if (toDelete == null) {
                    CommandHandler.sendMessage(sender, translate(sender, "commands.account.no_account"));
                    return;
                }
                DatabaseHelper.deleteAccount(toDelete);
                CommandHandler.sendMessage(sender, translate(sender, "commands.account.delete"));
            }
            case "resetpass" -> {
                if (args.size() < 2 || args.size() > 3) {
                    this.sendUsageMessage(sender);
                    return;
                }
                var username = args.get(1);
                Account toUpdate = DatabaseHelper.getAccountByName(username);
                if (toUpdate == null) {
                    CommandHandler.sendMessage(sender, translate(sender, "commands.account.no_account"));
                    return;
                }

                // Make sure the player cannot stay logged in with the old password.
                kickAccount(toUpdate);
                if (args.size() == 3 && !args.get(2).isEmpty()) {
                    toUpdate.setPassword(
                            BCrypt.withDefaults().hashToString(12, args.get(2).toCharArray()));
                    CommandHandler.sendMessage(sender, "Password Updated.");
                } else {
                    toUpdate.setPassword(null);
                    CommandHandler.sendMessage(sender, "Password Cleared.");
                }
                toUpdate.save();
            }
            case "list" -> {
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
                                                                                ? this.getPlayerUid(acc)
                                                                                : acc.getReservedPlayerUid()))
                                        .collect(Collectors.joining("\n")));
            }
        }
    }

    /**
     * Returns the UID of the player associated with the given account. If the player is not found,
     * returns "no UID".
     *
     * @param account The account to get the UID of.
     * @return The UID of the player associated with the given account.
     */
    private String getPlayerUid(Account account) {
        var player = DatabaseHelper.getPlayerByAccount(account, Player.class);
        return player == null ? "no UID" : String.valueOf(player.getUid());
    }

    private void kickAccount(Account account) {
        Player player = Grasscutter.getGameServer().getPlayerByAccountId(account.getId());
        if (player != null) {
            player.getSession().close();
        }
    }
}
