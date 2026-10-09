package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.event.entity.EntityDamageEvent;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(label = "entity", permission = "server.entity")
public final class EntityCommand implements CommandHandler {

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "entity")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<configId>")
        private int configId;

        @Option(names = "--state", defaultValue = "-1")
        private int state;

        @Option(names = "--ai", defaultValue = "-1")
        private int ai;

        @Option(names = "--max-hp", defaultValue = "-1")
        private int maxHp;

        @Option(names = "--hp", defaultValue = "-1")
        private int hp;

        @Option(names = "--atk", defaultValue = "-1")
        private int atk;

        @Option(names = "--def", defaultValue = "-1")
        private int def;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            GameEntity entity = targetPlayer.getScene().getFirstEntityByConfigId(configId);
            if (entity == null) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.entity.not_found_error"));
                return;
            }

            applyFightProps(entity, maxHp, hp, atk, def);
            if (state != -1 && entity instanceof EntityGadget gadget) {
                gadget.updateState(state);
            }
            if (ai != -1 && entity instanceof EntityMonster monster) {
                monster.setAiId(ai);
            }
            CommandOutput.sendMessage(sender, translate(sender, "commands.status.success"));
        }
    }

    private static void applyFightProps(
            GameEntity entity, int maxHp, int hp, int atk, int def) {
        var changed = new ArrayList<FightProperty>();
        if (maxHp != -1) {
            setFightProperty(entity, FightProperty.FIGHT_PROP_MAX_HP, maxHp, changed);
        }
        if (hp != -1) {
            float targetHp = hp == 0 ? Float.MAX_VALUE : hp;
            float oldHp = entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
            setFightProperty(entity, FightProperty.FIGHT_PROP_CUR_HP, targetHp, changed);
            entity.runLuaCallbacks(
                    new EntityDamageEvent(entity, oldHp - targetHp, ElementType.None, null));
        }
        if (atk != -1) {
            setFightProperty(entity, FightProperty.FIGHT_PROP_ATTACK, atk, changed);
            setFightProperty(entity, FightProperty.FIGHT_PROP_CUR_ATTACK, atk, changed);
        }
        if (def != -1) {
            setFightProperty(entity, FightProperty.FIGHT_PROP_DEFENSE, def, changed);
            setFightProperty(entity, FightProperty.FIGHT_PROP_CUR_DEFENSE, def, changed);
        }
        if (!changed.isEmpty()) {
            entity.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(entity, changed));
        }
    }

    private static void setFightProperty(
            GameEntity entity, FightProperty property, float value, List<FightProperty> changed) {
        entity.setFightProperty(property, value);
        changed.add(property);
    }
}
