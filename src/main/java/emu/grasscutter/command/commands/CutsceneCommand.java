package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketCutsceneBeginNotify;
import java.util.Locale;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "cutscene",
        aliases = {"c"},
        permission = "player.cutscene",
        permissionTargeted = "player.cutscene.others")
public final class CutsceneCommand implements CommandHandler {
    private static final int MAX_RESULTS = 30;

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var root = new CommandLine(new Play(sender, targetPlayer));
        root.addSubcommand("list", new ListCutscenes(sender));
        return root;
    }

    @CommandLine.Command(name = "cutscene")
    private static final class Play implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<cutsceneId>")
        private int cutsceneId;

        private Play(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            var data = GameData.getCutsceneDataMap().get(cutsceneId);
            if (data != null) {
                CommandOutput.sendMessage(
                        sender, "Playing %d: %s".formatted(cutsceneId, data.getPath()));
            }
            targetPlayer.sendPacket(new PacketCutsceneBeginNotify(cutsceneId));
        }
    }

    @CommandLine.Command(name = "list")
    private static final class ListCutscenes implements Runnable {
        private final Player sender;

        @Parameters(index = "0..*", arity = "0..*", paramLabel = "[search]")
        private String[] searchWords = new String[0];

        private ListCutscenes(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            String search = String.join(" ", searchWords);
            String needle = search.toLowerCase(Locale.ROOT);
            var matches =
                    GameData.getCutsceneDataMap().values().stream()
                            .filter(data -> data.getPath() != null)
                            .filter(
                                    data ->
                                            needle.isEmpty()
                                                    || data.getPath()
                                                            .toLowerCase(Locale.ROOT)
                                                            .contains(needle))
                            .toList();

            if (matches.isEmpty()) {
                CommandOutput.sendMessage(
                        sender, "No cutscene path contains '%s'.".formatted(search));
                return;
            }

            CommandOutput.sendMessage(
                    sender,
                    "%d cutscene(s)%s:"
                            .formatted(
                                    matches.size(),
                                    needle.isEmpty()
                                            ? ""
                                            : " matching '%s'".formatted(search)));
            matches.stream()
                    .limit(MAX_RESULTS)
                    .forEach(
                            data ->
                                    CommandOutput.sendMessage(
                                            sender,
                                            "  %d - %s".formatted(data.getId(), data.getPath())));
            if (matches.size() > MAX_RESULTS) {
                CommandOutput.sendMessage(
                        sender,
                        "  ...and %d more; narrow the search."
                                .formatted(matches.size() - MAX_RESULTS));
            }
        }
    }
}
