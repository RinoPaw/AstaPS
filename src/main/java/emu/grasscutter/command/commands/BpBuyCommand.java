package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify;
import emu.grasscutter.server.packet.send.PacketBeyondBattlePassCurScheduleUpdateNotify;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "bpbuy",
        permission = "player.setprop",
        permissionTargeted = "player.setprop.others")
public final class BpBuyCommand implements PicocliCommandHandler {

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "bpbuy")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<levels>")
        private int levels;

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
            if (levels <= 0) {
                CommandHandler.sendMessage(sender, "levels must be > 0");
                return;
            }

            BattlePassManager battlePass = player.getBattlePassManager();
            if (battlePass == null) {
                CommandHandler.sendMessage(sender, "No battle pass manager.");
                return;
            }

            int purchasable = Math.min(levels, Math.max(0, 50 - battlePass.getLevel()));
            if (purchasable <= 0) {
                CommandHandler.sendMessage(sender, "Already at max BP level.");
                return;
            }

            int cost = 150 * purchasable;
            if (player.getPrimogems() < cost) {
                CommandHandler.sendMessage(
                        sender, "Need " + cost + " primogems, have " + player.getPrimogems());
                return;
            }

            player.setPrimogems(player.getPrimogems() - cost);
            battlePass.setLevel(battlePass.getLevel() + purchasable);
            battlePass.save();
            player.sendPacket(new PacketBattlePassCurScheduleUpdateNotify(player));
            player.sendPacket(new PacketBeyondBattlePassCurScheduleUpdateNotify(player));
            CommandHandler.sendMessage(
                    sender,
                    "Bought "
                            + purchasable
                            + " BP levels for "
                            + cost
                            + " primogems. Now level "
                            + battlePass.getLevel());
        }
    }
}
