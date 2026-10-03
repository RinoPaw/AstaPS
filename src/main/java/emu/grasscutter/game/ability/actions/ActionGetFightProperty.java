package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.SkirkCunningHelper;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;

@AbilityAction(AbilityModifierAction.Type.GetFightProperty)
public final class ActionGetFightProperty extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        var owner = ability.getOwner();
        var resolvedTarget = target != null ? target : owner;
        if (owner == null || resolvedTarget == null || action.globalValueKey == null) return false;

        // GetFightProperty separates the entity whose property is read from the entity receiving the
        // global value. When no source is named, preserve the historical owner behavior.
        GameEntity source = owner;
        if (action.fightPropSourceTarget != null && !action.fightPropSourceTarget.isEmpty()) {
            var base = target != null ? target : owner;
            var resolvedSource = getTarget(ability, base, action.fightPropSourceTarget);
            if (resolvedSource != null) source = resolvedSource;
        }

        var properties = new Object2FloatOpenHashMap<String>();
        for (var property : FightProperty.values()) {
            properties.put(property.name(), source.getFightProperty(property));
        }
        properties.putAll(ability.getAbilitySpecials());

        float fightPropertyValue = action.fightProp == null ? 0f : action.fightProp.get(properties, 0f);
        if (Math.abs(fightPropertyValue) < 0.0001f && action.fightProp != null) {
            try {
                String name = action.fightProp.toString();
                if (name != null && properties.containsKey(name)) {
                    fightPropertyValue = properties.getFloat(name);
                }
            } catch (Throwable ignored) {
            }
            if (Math.abs(fightPropertyValue) < 0.0001f) {
                fightPropertyValue =
                        source.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY);
            }
        }

        resolvedTarget.getGlobalAbilityValues().put(action.globalValueKey, fightPropertyValue);
        resolvedTarget.onAbilityValueUpdate();
        if (resolvedTarget.getScene() != null && resolvedTarget.getScene().getHost() != null) {
            resolvedTarget
                    .getScene()
                    .getHost()
                    .sendPacket(
                            new PacketServerGlobalValueChangeNotify(
                                    resolvedTarget, action.globalValueKey, fightPropertyValue));
        }
        if (SkirkCunningHelper.isSkirk(owner)) SkirkCunningHelper.syncNyxFromSpecial(owner);

        if (emu.grasscutter.DebugConstants.LOG_ABILITIES) {
            Grasscutter.getLogger()
                    .debug(
                            "GetFightProperty {} from {} -> {} on {}",
                            action.fightProp,
                            source,
                            fightPropertyValue,
                            resolvedTarget);
        }
        return true;
    }
}
