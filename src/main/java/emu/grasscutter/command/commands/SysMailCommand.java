package emu.grasscutter.command.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.mail.SystemMailHelper;
import emu.grasscutter.game.player.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        label = "sysmail",
        permission = "server.sendmail",
        aliases = {"systemmail", "smail"},
        targetRequirement = Command.TargetRequirement.NONE)
public final class SysMailCommand implements PicocliCommandHandler {
    private record Attachment(int itemId, int count) {}

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.registerConverter(
                Attachment.class,
                value -> {
                    String[] parts = value.split(":", 2);
                    if (parts.length != 2) {
                        throw new CommandLine.TypeConversionException(
                                "Attachment must be <itemId>:<count>");
                    }
                    try {
                        int itemId = Integer.parseInt(parts[0]);
                        int count = Integer.parseInt(parts[1]);
                        if (itemId <= 0 || count <= 0) throw new NumberFormatException();
                        return new Attachment(itemId, count);
                    } catch (NumberFormatException ignored) {
                        throw new CommandLine.TypeConversionException(
                                "Attachment must contain positive integers: <itemId>:<count>");
                    }
                });
        commandLine.addSubcommand("send", new Send(sender));
        commandLine.addSubcommand("update", new Update(sender));
        commandLine.addSubcommand("retitle", new Retitle(sender));
        commandLine.addSubcommand("delete", new Delete(sender));
        commandLine.addSubcommand("deleteall", new DeleteAll(sender));
        return commandLine;
    }

    @CommandLine.Command(name = "sysmail")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            SysMailCommand.this.sendUsageMessage(sender);
        }
    }

    @CommandLine.Command(name = "send")
    private static final class Send implements Runnable {
        private final Player sender;

        @Option(names = "--title", required = true, arity = "1..*", paramLabel = "<title>")
        private List<String> title;

        @Option(names = "--body", required = true, arity = "1..*", paramLabel = "<body>")
        private List<String> body;

        @Option(names = "--sender", arity = "1..*", paramLabel = "<sender>")
        private List<String> senderName;

        @Option(names = "--item", paramLabel = "<itemId:count>")
        private List<Attachment> attachments = new ArrayList<>();

        private Send(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            Mail mail = SystemMailHelper.newSystemMail();
            mail.mailContent.title = SystemMailHelper.normalizeTitle(String.join(" ", title));
            mail.mailContent.content = String.join(" ", body);
            if (senderName != null && !senderName.isEmpty()) {
                mail.mailContent.sender = String.join(" ", senderName);
            }
            for (Attachment attachment : attachments) {
                mail.itemList.add(new Mail.MailItem(attachment.itemId(), attachment.count()));
            }

            int[] sent = {0};
            DatabaseHelper.getByGameClass(Player.class)
                    .forEach(
                            storedPlayer -> {
                                Player target =
                                        Objects.requireNonNullElse(
                                                Grasscutter.getGameServer()
                                                        .getPlayerByUid(storedPlayer.getUid(), false),
                                                storedPlayer);
                                target.sendMail(cloneMail(mail));
                                sent[0]++;
                            });
            CommandHandler.sendMessage(
                    sender,
                    "Sent a system mail to " + sent[0] + " player(s): " + mail.mailContent.title);
        }
    }

    @CommandLine.Command(name = "update")
    private static final class Update implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<titleKeyword>")
        private String keyword;

        @Parameters(index = "1..*", arity = "1..*", paramLabel = "<newBody>")
        private List<String> body;

        private Update(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            int count = SystemMailHelper.updateContentByTitle(keyword, String.join(" ", body));
            CommandHandler.sendMessage(
                    sender,
                    count > 0
                            ? "Updated the body of " + count + " system mail(s)."
                            : "No matching system mail.");
        }
    }

    @CommandLine.Command(name = "retitle")
    private static final class Retitle implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<titleKeyword>")
        private String keyword;

        @Parameters(index = "1..*", arity = "1..*", paramLabel = "<newTitle>")
        private List<String> title;

        private Retitle(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            int count = SystemMailHelper.updateTitle(keyword, String.join(" ", title));
            CommandHandler.sendMessage(
                    sender,
                    count > 0
                            ? "Updated the title of " + count + " system mail(s)."
                            : "No matching system mail.");
        }
    }

    @CommandLine.Command(name = "delete")
    private static final class Delete implements Runnable {
        private final Player sender;

        @Parameters(index = "0..*", arity = "1..*", paramLabel = "<titleKeyword>")
        private List<String> keyword;

        private Delete(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            int count = SystemMailHelper.deleteByTitle(String.join(" ", keyword));
            CommandHandler.sendMessage(
                    sender,
                    count > 0
                            ? "Deleted " + count + " system mail(s)."
                            : "No matching system mail.");
        }
    }

    @CommandLine.Command(name = "deleteall")
    private static final class DeleteAll implements Runnable {
        private final Player sender;

        private DeleteAll(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            int count = SystemMailHelper.deleteAllProtected();
            CommandHandler.sendMessage(
                    sender,
                    count > 0
                            ? "Deleted all " + count + " system mail(s)."
                            : "There is no system mail to delete.");
        }
    }

    private static Mail cloneMail(Mail mail) {
        Mail copy = SystemMailHelper.newSystemMail();
        copy.mailContent.title = mail.mailContent.title;
        copy.mailContent.content = mail.mailContent.content;
        copy.mailContent.sender = mail.mailContent.sender;
        copy.itemList.addAll(mail.itemList);
        return copy;
    }
}
