package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
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
        label = "bppaid",
        permission = "player.setprop",
        permissionTargeted = "player.setprop.others")
public final class BpPaidCommand implements PicocliCommandHandler {
    private record PaidFlag(boolean value) {}

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Args(sender, targetPlayer));
        commandLine.registerConverter(
                PaidFlag.class,
                value -> {
                    return switch (value.trim().toLowerCase(Locale.ROOT)) {
                        case "true", "on", "1", "yes", "paid" -> new PaidFlag(true);
                        case "false", "off", "0", "no", "free" -> new PaidFlag(false);
                        default -> throw new CommandLine.TypeConversionException(
                                "Expected true|false|on|off|1|0");
                    };
                });
        return commandLine;
    }

    @CommandLine.Command(name = "bppaid")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[true|false]")
        private PaidFlag paid;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            Player player = targetPlayer != null ? targetPlayer : sender;
            if (player == null) {
                CommandHandler.sendMessage(sender, "No player.");
                return;
            }

            BattlePassManager battlePass = player.getBattlePassManager();
            if (battlePass == null) {
                CommandHandler.sendMessage(sender, "No battle pass manager.");
                return;
            }

            if (paid == null) {
                CommandHandler.sendMessage(sender, "Pearl BP paid=" + battlePass.isPaid());
                return;
            }

            if (!BattlePassCompatHelper.setPaidFlag(battlePass, paid.value())) {
                CommandHandler.sendMessage(sender, "setPaidFlag failed");
                return;
            }

            battlePass.save();
            player.sendPacket(new PacketBattlePassAllDataNotify(player));
            player.sendPacket(new PacketBattlePassCurScheduleUpdateNotify(player));
            player.sendPacket(new PacketBeyondBattlePassAllDataNotify(player));
            player.sendPacket(new PacketBeyondBattlePassCurScheduleUpdateNotify(player));
            CommandHandler.sendMessage(
                    sender,
                    "Pearl BP paid set to "
                            + paid.value()
                            + " (isPaid="
                            + battlePass.isPaid()
                            + ")");
        }
    }
}
