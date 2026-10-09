package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketServerAnnounceNotify;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "announce",
        permission = "server.announce",
        aliases = {"a"},
        targetRequirement = Command.TargetRequirement.NONE)
public final class AnnounceCommand implements CommandHandler {

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Broadcast(sender));
        commandLine.addSubcommand("tpl", new Template(sender));
        commandLine.addSubcommand("refresh", new Refresh(sender));
        commandLine.addSubcommand("revoke", new Revoke(sender));
        return commandLine;
    }

    @CommandLine.Command(name = "announce")
    private static final class Broadcast implements Runnable {
        private final Player sender;

        @Parameters(index = "0..*", arity = "1..*", paramLabel = "<content>")
        private List<String> content;

        private Broadcast(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            var manager = Grasscutter.getGameServer().getAnnouncementSystem();
            int id = ThreadLocalRandom.current().nextInt(10000, 99999);
            String text = String.join(" ", content);
            manager.getOnlinePlayers()
                    .forEach(player -> player.sendPacket(new PacketServerAnnounceNotify(text, id)));
            CommandOutput.sendMessage(sender, translate(sender, "commands.announce.send_success", id));
        }
    }

    @CommandLine.Command(name = "tpl")
    private static final class Template implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<templateId>")
        private int templateId;

        private Template(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            var manager = Grasscutter.getGameServer().getAnnouncementSystem();
            var template = manager.getAnnounceConfigItemMap().get(templateId);
            if (template == null) {
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.announce.not_found", templateId));
                return;
            }
            manager.broadcast(Collections.singletonList(template));
            CommandOutput.sendMessage(
                    sender, translate(sender, "commands.announce.send_success", template.getTemplateId()));
        }
    }

    @CommandLine.Command(name = "refresh")
    private static final class Refresh implements Runnable {
        private final Player sender;

        private Refresh(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            int count = Grasscutter.getGameServer().getAnnouncementSystem().refresh();
            CommandOutput.sendMessage(
                    sender, translate(sender, "commands.announce.refresh_success", count));
        }
    }

    @CommandLine.Command(name = "revoke")
    private static final class Revoke implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<templateId>")
        private int templateId;

        private Revoke(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            Grasscutter.getGameServer().getAnnouncementSystem().revoke(templateId);
            CommandOutput.sendMessage(
                    sender, translate(sender, "commands.announce.revoke_done", templateId));
        }
    }
}
