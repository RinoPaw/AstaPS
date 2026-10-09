package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import java.util.HashMap;
import java.util.Map;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "setStats",
        aliases = {"stats", "stat"},
        permission = "player.setstats",
        permissionTargeted = "player.setstats.others")
public final class SetStatsCommand implements CommandHandler {
    private final Map<String, Stat> stats = new HashMap<>();

    private record StatArg(Stat stat) {}

    public SetStatsCommand() {
        for (String key : FightProperty.getShortNames()) {
            stats.put(key, new Stat(FightProperty.getPropByShortName(key)));
        }
        for (FightProperty prop : FightProperty.values()) {
            String name = prop.toString().substring(10);
            String key = name.toLowerCase();
            name = name.substring(1);
            stats.put(key, new Stat(name, prop));
        }

        stats.put("mhp", stats.get("maxhp"));
        stats.put("hp", stats.get("_cur_hp"));
        stats.put("atk", stats.get("_cur_attack"));
        stats.put("def", stats.get("_cur_defense"));
        stats.put("atkb", stats.get("_base_attack"));
        stats.put("eanemo", stats.get("anemo%"));
        stats.put("ecryo", stats.get("cryo%"));
        stats.put("edendro", stats.get("dendro%"));
        stats.put("edend", stats.get("dendro%"));
        stats.put("eelectro", stats.get("electro%"));
        stats.put("eelec", stats.get("electro%"));
        stats.put("ethunder", stats.get("electro%"));
        stats.put("egeo", stats.get("geo%"));
        stats.put("ehydro", stats.get("hydro%"));
        stats.put("epyro", stats.get("pyro%"));
        stats.put("ephys", stats.get("phys%"));
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new ImplicitSet(sender, targetPlayer));
        commandLine.registerConverter(
                StatArg.class,
                value -> {
                    Stat stat = stats.get(value.toLowerCase());
                    if (stat == null) {
                        throw new CommandLine.TypeConversionException("Unknown stat: " + value);
                    }
                    return new StatArg(stat);
                });
        commandLine.addSubcommand("set", new ExplicitSet(sender, targetPlayer));
        commandLine.addSubcommand("lock", new Lock(sender, targetPlayer));
        commandLine.addSubcommand("freeze", new Lock(sender, targetPlayer));
        commandLine.addSubcommand("unlock", new Unlock(sender, targetPlayer));
        commandLine.addSubcommand("unfreeze", new Unlock(sender, targetPlayer));
        return commandLine;
    }

    @CommandLine.Command(name = "setStats")
    private final class ImplicitSet implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<stat>")
        private StatArg stat;

        @Parameters(index = "1", paramLabel = "<value>")
        private String value;

        private ImplicitSet(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            applySet(sender, targetPlayer, stat.stat(), value);
        }
    }

    private final class ExplicitSet implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<stat>")
        private StatArg stat;

        @Parameters(index = "1", paramLabel = "<value>")
        private String value;

        private ExplicitSet(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            applySet(sender, targetPlayer, stat.stat(), value);
        }
    }

    private final class Lock implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<stat>")
        private StatArg stat;

        @Parameters(index = "1", arity = "0..1", paramLabel = "[value]")
        private String value;

        private Lock(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            EntityAvatar entity = targetPlayer.getTeamManager().getCurrentAvatarEntity();
            Avatar avatar = entity.getAvatar();
            float parsed;
            if (value == null) {
                parsed = avatar.getFightProperty(stat.stat().prop);
            } else {
                try {
                    parsed = parsePercent(value);
                } catch (NumberFormatException ignored) {
                    CommandOutput.sendTranslatedMessage(sender, "commands.generic.invalid.statValue");
                    return;
                }
            }

            avatar.getFightPropOverrides().put(stat.stat().prop.getId(), parsed);
            avatar.recalcStats();
            report(sender, targetPlayer, Action.ACTION_LOCK, stat.stat(), parsed);
        }
    }

    private final class Unlock implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<stat>")
        private StatArg stat;

        private Unlock(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            Avatar avatar = targetPlayer.getTeamManager().getCurrentAvatarEntity().getAvatar();
            float previous = avatar.getFightProperty(stat.stat().prop);
            avatar.getFightPropOverrides().remove(stat.stat().prop.getId());
            avatar.recalcStats();
            report(sender, targetPlayer, Action.ACTION_UNLOCK, stat.stat(), previous);
        }
    }

    private void applySet(Player sender, Player targetPlayer, Stat stat, String text) {
        float value;
        try {
            value = parsePercent(text);
        } catch (NumberFormatException ignored) {
            CommandOutput.sendTranslatedMessage(sender, "commands.generic.invalid.statValue");
            return;
        }

        EntityAvatar entity = targetPlayer.getTeamManager().getCurrentAvatarEntity();
        entity.setFightProperty(stat.prop, value);
        entity.getWorld().broadcastPacket(new PacketEntityFightPropUpdateNotify(entity, stat.prop));
        report(sender, targetPlayer, Action.ACTION_SET, stat, value);
    }

    private void report(Player sender, Player targetPlayer, Action action, Stat stat, float value) {
        String valueStr =
                FightProperty.isPercentage(stat.prop)
                        ? String.format("%.1f%%", value * 100f)
                        : String.format("%.0f", value);
        if (targetPlayer == sender) {
            CommandOutput.sendTranslatedMessage(sender, action.messageKeySelf, stat.name, valueStr);
        } else {
            String uidStr = targetPlayer.getAccount().getId();
            CommandOutput.sendTranslatedMessage(
                    sender, action.messageKeyOther, stat.name, uidStr, valueStr);
        }
    }

    public static float parsePercent(String input) throws NumberFormatException {
        return input.endsWith("%")
                ? Float.parseFloat(input.substring(0, input.length() - 1)) / 100f
                : Float.parseFloat(input);
    }

    private enum Action {
        ACTION_SET("commands.generic.set_to", "commands.generic.set_for_to"),
        ACTION_LOCK("commands.setStats.locked_to", "commands.setStats.locked_for_to"),
        ACTION_UNLOCK("commands.setStats.unlocked", "commands.setStats.unlocked_for");

        private final String messageKeySelf;
        private final String messageKeyOther;

        Action(String messageKeySelf, String messageKeyOther) {
            this.messageKeySelf = messageKeySelf;
            this.messageKeyOther = messageKeyOther;
        }
    }

    private static final class Stat {
        private final String name;
        private final FightProperty prop;

        private Stat(FightProperty prop) {
            this(prop.toString(), prop);
        }

        private Stat(String name, FightProperty prop) {
            this.name = name;
            this.prop = prop;
        }
    }
}
