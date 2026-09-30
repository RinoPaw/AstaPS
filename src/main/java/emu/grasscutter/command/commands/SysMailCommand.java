/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.command.Command
 *  emu.grasscutter.command.Command$TargetRequirement
 *  emu.grasscutter.command.CommandHandler
 *  emu.grasscutter.database.DatabaseHelper
 *  emu.grasscutter.game.mail.Mail
 *  emu.grasscutter.game.mail.Mail$MailItem
 *  emu.grasscutter.game.player.Player
 */
package emu.grasscutter.command.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.mail.SystemMailHelper;
import emu.grasscutter.game.player.Player;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

@Command(label="sysmail", usage={"all", "update <title-keyword> <new-body>", "retitle <title-keyword> <new-title>", "delete <title-keyword>", "deleteall", "help"}, permission="server.sendmail", aliases={"systemmail", "smail"}, targetRequirement=Command.TargetRequirement.NONE)
public final class SysMailCommand
implements CommandHandler {
    private static final HashMap<Integer, Draft> drafts = new HashMap<>();

    private static int senderKey(Player player) {
        return player != null ? player.getUid() : -1;
    }

    public void execute(Player player, Player player2, List<String> list) {
        String string;
        if (list.isEmpty()) {
            this.sendUsageMessage(player, new String[0]);
            CommandHandler.sendMessage((Player)player, (String)"System mail: titles are prefixed automatically, players cannot delete them, and update/delete manage them.");
            return;
        }
        switch (string = list.get(0).toLowerCase()) {
            case "help": {
                this.sendUsageMessage(player, new String[0]);
                CommandHandler.sendMessage((Player)player, (String)"Send to everyone: /sysmail all -> title -> body -> sender -> finish");
                CommandHandler.sendMessage((Player)player, (String)"Edit the body:  /sysmail update <title-keyword> <new body...>");
                CommandHandler.sendMessage((Player)player, (String)"Edit the title: /sysmail retitle <title-keyword> <new title>");
                CommandHandler.sendMessage((Player)player, (String)"Delete:         /sysmail delete <title-keyword>");
                CommandHandler.sendMessage((Player)player, (String)"Delete all:     /sysmail deleteall");
                break;
            }
            case "all": {
                this.startDraft(player);
                break;
            }
            case "stop": {
                drafts.remove(SysMailCommand.senderKey(player));
                CommandHandler.sendMessage((Player)player, (String)"Cancelled the system mail draft.");
                break;
            }
            case "finish": {
                this.finishDraft(player);
                break;
            }
            case "update": {
                String string2;
                if (list.size() < 3) {
                    CommandHandler.sendMessage((Player)player, (String)"Usage: /sysmail update <title-keyword> <new-body>");
                    return;
                }
                String string3 = list.get(1);
                int n = SystemMailHelper.updateContentByTitle(string3, string2 = String.join((CharSequence)" ", list.subList(2, list.size())));
                CommandHandler.sendMessage((Player)player, (String)(n > 0 ? "Updated the body of " + n + " system mail(s)." : "No matching system mail."));
                break;
            }
            case "retitle": {
                if (list.size() < 3) {
                    CommandHandler.sendMessage((Player)player, (String)"Usage: /sysmail retitle <title-keyword> <new-title>");
                    return;
                }
                int n = SystemMailHelper.updateTitle(list.get(1), String.join((CharSequence)" ", list.subList(2, list.size())));
                CommandHandler.sendMessage((Player)player, (String)(n > 0 ? "Updated the title of " + n + " system mail(s)." : "No matching system mail."));
                break;
            }
            case "delete": 
            case "del": 
            case "remove": {
                if (list.size() < 2) {
                    CommandHandler.sendMessage((Player)player, (String)"Usage: /sysmail delete <title-keyword>");
                    return;
                }
                String string4 = String.join((CharSequence)" ", list.subList(1, list.size()));
                int n = SystemMailHelper.deleteByTitle(string4);
                CommandHandler.sendMessage((Player)player, (String)(n > 0 ? "Deleted " + n + " system mail(s)." : "No matching system mail."));
                break;
            }
            case "deleteall": {
                int n = SystemMailHelper.deleteAllProtected();
                CommandHandler.sendMessage((Player)player, (String)(n > 0 ? "Deleted all " + n + " system mail(s)." : "There is no system mail to delete."));
                break;
            }
            default: {
                this.continueDraft(player, list);
            }
        }
    }

    private void startDraft(Player player) {
        drafts.put(SysMailCommand.senderKey(player), new Draft());
        CommandHandler.sendMessage((Player)player, (String)"Writing a system mail. Next: type the title");
    }

    private void continueDraft(Player player, List<String> list) {
        int n = SysMailCommand.senderKey(player);
        Draft draft = drafts.get(n);
        if (draft == null) {
            CommandHandler.sendMessage((Player)player, (String)"Run /sysmail all first");
            return;
        }
        String string = String.join((CharSequence)" ", list);
        switch (draft.stage) {
            case 0: {
                draft.mail.mailContent.title = SystemMailHelper.normalizeTitle(string);
                draft.stage = 1;
                CommandHandler.sendMessage((Player)player, (String)("Title: " + draft.mail.mailContent.title));
                CommandHandler.sendMessage((Player)player, (String)"Next: type the body");
                break;
            }
            case 1: {
                draft.mail.mailContent.content = string;
                draft.stage = 2;
                CommandHandler.sendMessage((Player)player, (String)"Next: type the sender name");
                break;
            }
            case 2: {
                draft.mail.mailContent.sender = string;
                draft.stage = 3;
                CommandHandler.sendMessage((Player)player, (String)"Attach items as <itemId> <count>, or run /sysmail finish");
                break;
            }
            case 3: {
                if (list.size() >= 2) {
                    try {
                        int n2 = Integer.parseInt(list.get(0));
                        int n3 = Integer.parseInt(list.get(1));
                        draft.mail.itemList.add(new Mail.MailItem(n2, n3));
                        CommandHandler.sendMessage((Player)player, (String)("Attached " + n2 + " x" + n3));
                    }
                    catch (NumberFormatException numberFormatException) {
                        CommandHandler.sendMessage((Player)player, (String)"Attachment format: <itemId> <count>");
                    }
                    break;
                }
                CommandHandler.sendMessage((Player)player, (String)"Attachment format: <itemId> <count>, or /sysmail finish");
                break;
            }
            default: {
                CommandHandler.sendMessage((Player)player, (String)"Run /sysmail finish to send");
            }
        }
    }

    private void finishDraft(Player player2) {
        int n = SysMailCommand.senderKey(player2);
        Draft draft = drafts.remove(n);
        if (draft == null || draft.stage < 2) {
            CommandHandler.sendMessage((Player)player2, (String)"The system mail needs at least a title and a body.");
            return;
        }
        int[] nArray = new int[]{0};
        DatabaseHelper.getByGameClass(Player.class).forEach(player -> {
            Player target = Objects.requireNonNullElse(Grasscutter.getGameServer().getPlayerByUid(player.getUid(), false), player);
            target.sendMail(SysMailCommand.cloneMail(draft.mail));
            nArray[0] = nArray[0] + 1;
        });
        CommandHandler.sendMessage((Player)player2, (String)("Sent a system mail to " + nArray[0] + " player(s): " + draft.mail.mailContent.title));
    }

    private static Mail cloneMail(Mail mail) {
        Mail mail2 = SystemMailHelper.newSystemMail();
        mail2.mailContent.title = mail.mailContent.title;
        mail2.mailContent.content = mail.mailContent.content;
        mail2.mailContent.sender = mail.mailContent.sender;
        mail2.itemList.addAll(mail.itemList);
        return mail2;
    }

    private static final class Draft {
        Mail mail = SystemMailHelper.newSystemMail();
        int stage = 0;

        Draft() {
        }
    }
}

