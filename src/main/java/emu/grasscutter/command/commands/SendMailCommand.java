package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.player.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        label = "sendMail",
        permission = "server.sendmail",
        targetRequirement = Command.TargetRequirement.NONE)
public final class SendMailCommand implements PicocliCommandHandler {
    private record Recipient(Integer uid) {
        private boolean all() {
            return uid == null;
        }
    }

    private record Attachment(int itemId, int count, int level) {}

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Send(sender));
        commandLine.registerConverter(
                Recipient.class,
                value -> {
                    if (value.equalsIgnoreCase("all")) return new Recipient(null);
                    try {
                        return new Recipient(Integer.parseInt(value));
                    } catch (NumberFormatException ignored) {
                        throw new CommandLine.TypeConversionException("Recipient must be a UID or 'all'");
                    }
                });
        commandLine.registerConverter(
                Attachment.class,
                value -> {
                    String[] parts = value.split(":");
                    if (parts.length < 2 || parts.length > 3) {
                        throw new CommandLine.TypeConversionException(
                                "Attachment must be <itemId>:<count>[:level]");
                    }
                    try {
                        int itemId = Integer.parseInt(parts[0]);
                        int count = Integer.parseInt(parts[1]);
                        int level = parts.length == 3 ? Integer.parseInt(parts[2]) : 1;
                        if (itemId <= 0 || count <= 0 || level <= 0) throw new NumberFormatException();
                        return new Attachment(itemId, count, level);
                    } catch (NumberFormatException ignored) {
                        throw new CommandLine.TypeConversionException(
                                "Attachment values must be positive integers");
                    }
                });
        return commandLine;
    }

    @CommandLine.Command(name = "sendMail")
    private static final class Send implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<uid|all>")
        private Recipient recipient;

        @Option(names = "--title", required = true, arity = "1..*", paramLabel = "<title>")
        private List<String> title;

        @Option(names = "--body", required = true, arity = "1..*", paramLabel = "<body>")
        private List<String> body;

        @Option(names = "--sender", arity = "1..*", paramLabel = "<sender>")
        private List<String> senderName;

        @Option(names = "--item", paramLabel = "<itemId:count[:level]>")
        private List<Attachment> attachments = new ArrayList<>();

        private Send(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            Mail mail = new Mail();
            mail.mailContent.title = String.join(" ", title);
            mail.mailContent.content = String.join(" ", body);
            if (senderName != null && !senderName.isEmpty()) {
                mail.mailContent.sender = String.join(" ", senderName);
            }
            for (Attachment attachment : attachments) {
                mail.itemList.add(
                        new Mail.MailItem(
                                attachment.itemId(), attachment.count(), attachment.level()));
            }

            if (recipient.all()) {
                int[] count = {0};
                DatabaseHelper.getByGameClass(Player.class)
                        .forEach(
                                storedPlayer -> {
                                    Player target =
                                            Objects.requireNonNullElse(
                                                    Grasscutter.getGameServer()
                                                            .getPlayerByUid(storedPlayer.getUid(), false),
                                                    storedPlayer);
                                    target.sendMail(cloneMail(mail));
                                    count[0]++;
                                });
                CommandHandler.sendMessage(
                        sender,
                        translate(sender, "commands.sendMail.send_all_done")
                                + " ("
                                + count[0]
                                + ")");
                return;
            }

            Player stored = DatabaseHelper.getPlayerByUid(recipient.uid());
            if (stored == null) {
                CommandHandler.sendMessage(
                        sender,
                        translate(
                                sender,
                                "commands.sendMail.user_not_exist",
                                String.valueOf(recipient.uid())));
                return;
            }
            Player target =
                    Objects.requireNonNullElse(
                            Grasscutter.getGameServer().getPlayerByUid(recipient.uid(), false), stored);
            target.sendMail(mail);
            CommandHandler.sendMessage(
                    sender, translate(sender, "commands.sendMail.send_done", recipient.uid()));
        }
    }

    private static Mail cloneMail(Mail source) {
        Mail copy = new Mail();
        copy.mailContent.title = source.mailContent.title;
        copy.mailContent.content = source.mailContent.content;
        copy.mailContent.sender = source.mailContent.sender;
        copy.itemList.addAll(source.itemList);
        return copy;
    }
}
