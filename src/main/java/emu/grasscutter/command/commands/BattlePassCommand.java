package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.battlepass.BattlePassCompatHelper;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketBattlePassAllDataNotify;
import emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify;
import emu.grasscutter.server.packet.send.PacketBeyondBattlePassAllDataNotify;
import emu.grasscutter.server.packet.send.PacketBeyondBattlePassCurScheduleUpdateNotify;
import java.util.Locale;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "bp",
        aliases = {"battlepass"},
        permission = "player.setprop",
        permissionTargeted = "player.setprop.others")
public final class BattlePassCommand implements CommandHandler {
    private record PaidFlag(boolean value) {}

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.registerConverter(PaidFlag.class, BattlePassCommand::parsePaidFlag);
        commandLine.addSubcommand("buy", new Buy(sender, targetPlayer));
        commandLine.addSubcommand("paid", new Paid(sender, targetPlayer));
        return commandLine;
    }

    private static PaidFlag parsePaidFlag(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "on", "1", "yes", "paid" -> new PaidFlag(true);
            case "false", "off", "0", "no", "free" -> new PaidFlag(false);
            default -> throw new CommandLine.TypeConversionException(
                    "Expected true|false|on|off|1|0");
        };
    }

    @CommandLine.Command(name = "bp")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            BattlePassCommand.this.sendUsageMessage(sender);
        }
    }

    private abstract static class PlayerCommand implements Runnable {
        protected final Player sender;
        protected final Player targetPlayer;

        private PlayerCommand(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        protected Player player() {
            Player player = targetPlayer != null ? targetPlayer : sender;
            if (player == null) CommandOutput.sendMessage(sender, "No player.");
            return player;
        }

        protected BattlePassManager battlePass(Player player) {
            BattlePassManager battlePass = player.getBattlePassManager();
            if (battlePass == null) CommandOutput.sendMessage(sender, "No battle pass manager.");
            return battlePass;
        }
    }

    @CommandLine.Command(name = "buy")
    private static final class Buy extends PlayerCommand {
        @Parameters(index = "0", paramLabel = "<levels>")
        private int levels;

        private Buy(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            Player player = player();
            if (player == null) return;
            if (levels <= 0) {
                CommandOutput.sendMessage(sender, "levels must be > 0");
                return;
            }

            BattlePassManager battlePass = battlePass(player);
            if (battlePass == null) return;

            int purchasable = Math.min(levels, Math.max(0, 50 - battlePass.getLevel()));
            if (purchasable <= 0) {
                CommandOutput.sendMessage(sender, "Already at max BP level.");
                return;
            }

            int cost = 150 * purchasable;
            if (player.getPrimogems() < cost) {
                CommandOutput.sendMessage(
                        sender, "Need " + cost + " primogems, have " + player.getPrimogems());
                return;
            }

            player.setPrimogems(player.getPrimogems() - cost);
            battlePass.setLevel(battlePass.getLevel() + purchasable);
            battlePass.save();
            player.sendPacket(new PacketBattlePassCurScheduleUpdateNotify(player));
            player.sendPacket(new PacketBeyondBattlePassCurScheduleUpdateNotify(player));
            CommandOutput.sendMessage(
                    sender,
                    "Bought "
                            + purchasable
                            + " BP levels for "
                            + cost
                            + " primogems. Now level "
                            + battlePass.getLevel());
        }
    }

    @CommandLine.Command(name = "paid")
    private static final class Paid extends PlayerCommand {
        @Parameters(index = "0", arity = "0..1", paramLabel = "[true|false]")
        private PaidFlag paid;

        private Paid(Player sender, Player targetPlayer) {
            super(sender, targetPlayer);
        }

        @Override
        public void run() {
            Player player = player();
            if (player == null) return;
            BattlePassManager battlePass = battlePass(player);
            if (battlePass == null) return;

            if (paid == null) {
                CommandOutput.sendMessage(sender, "Pearl BP paid=" + battlePass.isPaid());
                return;
            }

            if (!BattlePassCompatHelper.setPaidFlag(battlePass, paid.value())) {
                CommandOutput.sendMessage(sender, "setPaidFlag failed");
                return;
            }

            battlePass.save();
            player.sendPacket(new PacketBattlePassAllDataNotify(player));
            player.sendPacket(new PacketBattlePassCurScheduleUpdateNotify(player));
            player.sendPacket(new PacketBeyondBattlePassAllDataNotify(player));
            player.sendPacket(new PacketBeyondBattlePassCurScheduleUpdateNotify(player));
            CommandOutput.sendMessage(
                    sender,
                    "Pearl BP paid set to "
                            + paid.value()
                            + " (isPaid="
                            + battlePass.isPaid()
                            + ")");
        }
    }
}
