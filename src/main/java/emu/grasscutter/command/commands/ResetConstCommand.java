package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.PicocliCommandHandler;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "resetConst",
        aliases = {"resetconstellation"},
        permission = "player.resetconstellation",
        permissionTargeted = "player.resetconstellation.others")
public final class ResetConstCommand implements PicocliCommandHandler {
    private enum Scope {
        ALL
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Args(sender, targetPlayer));
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        return commandLine;
    }

    @CommandLine.Command(name = "resetConst")
    private final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[all]")
        private Scope scope;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (scope == Scope.ALL) {
                targetPlayer.getAvatars().forEach(ResetConstCommand.this::resetConstellation);
                CommandHandler.sendMessage(sender, translate(sender, "commands.resetConst.reset_all"));
                return;
            }

            EntityAvatar entity = targetPlayer.getTeamManager().getCurrentAvatarEntity();
            if (entity == null) return;

            Avatar avatar = entity.getAvatar();
            resetConstellation(avatar);
            CommandHandler.sendMessage(
                    sender,
                    translate(sender, "commands.resetConst.success", avatar.getAvatarData().getName()));
        }
    }

    private void resetConstellation(Avatar avatar) {
        avatar.forceConstellationLevel(-1);
    }
}
