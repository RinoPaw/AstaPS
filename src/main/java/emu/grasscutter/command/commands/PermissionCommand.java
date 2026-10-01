package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import java.util.Objects;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "permission",
        permission = "permission",
        targetRequirement = Command.TargetRequirement.PLAYER)
public final class PermissionCommand implements PicocliCommandHandler {

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("add", new Add(sender, targetPlayer));
        commandLine.addSubcommand("remove", new Remove(sender, targetPlayer));
        commandLine.addSubcommand("clear", new Clear(sender, targetPlayer));
        commandLine.addSubcommand("list", new ListPermissions(sender, targetPlayer));
        return commandLine;
    }

    @CommandLine.Command(name = "permission")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            PermissionCommand.this.sendUsageMessage(sender);
        }
    }

    private abstract static class PermissionAction implements Runnable {
        protected final Player sender;
        protected final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<key>")
        private String key;

        private PermissionAction(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        protected Account account() {
            if (!Objects.equals(key, Configuration.HTTP_ENCRYPTION.keystorePassword)) {
                CommandHandler.sendMessage(sender != null ? sender : targetPlayer, "Wrong key");
                return null;
            }
            if (!Grasscutter.getPermissionHandler().EnablePermissionCommand()) {
                CommandHandler.sendTranslatedMessage(sender, "commands.generic.permission_error");
                return null;
            }
            Account account = targetPlayer.getAccount();
            if (account == null) {
                CommandHandler.sendMessage(sender, translate(sender, "commands.permission.account_error"));
            }
            return account;
        }
    }

    private static final class Add extends PermissionAction {
        @Parameters(index = "1", paramLabel = "<permission>")
        private String permission;

        private Add(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            Account account = account();
            if (account == null) return;
            if (account.addPermission(permission)) {
                CommandHandler.sendMessage(sender, translate(sender, "commands.permission.add"));
            } else {
                CommandHandler.sendMessage(sender, translate(sender, "commands.permission.has_error"));
            }
            account.save();
        }
    }

    private static final class Remove extends PermissionAction {
        @Parameters(index = "1", paramLabel = "<permission>")
        private String permission;

        private Remove(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            Account account = account();
            if (account == null) return;
            if (account.removePermission(permission)) {
                CommandHandler.sendMessage(sender, translate(sender, "commands.permission.remove"));
            } else {
                CommandHandler.sendMessage(
                        sender, translate(sender, "commands.permission.not_have_error"));
            }
            account.save();
        }
    }

    private static final class Clear extends PermissionAction {
        private Clear(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            Account account = account();
            if (account == null) return;
            account.clearPermission();
            account.save();
            CommandHandler.sendMessage(sender, translate(sender, "commands.permission.remove"));
        }
    }

    private static final class ListPermissions extends PermissionAction {
        private ListPermissions(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            Account account = account();
            if (account == null) return;
            CommandHandler.sendMessage(sender, String.join("\n", account.getPermissions()));
        }
    }
}
