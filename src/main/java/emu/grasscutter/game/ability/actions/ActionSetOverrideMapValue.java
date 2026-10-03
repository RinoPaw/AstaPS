package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(AbilityModifierAction.Type.SetOverrideMapValue)
public final class ActionSetOverrideMapValue extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        var properties = propertiesFor(ability);
        float value = action.ratio.get(properties, 0f);

        if (action.useLimitRange) {
            float minValue = action.minValue.get(properties, 0f);
            float maxValue = action.maxValue.get(properties, 0f);
            value = Math.max(minValue, Math.min(value, maxValue));
        }

        ability.getAbilitySpecials().put(action.overrideMapKey, value);
        return true;
    }
}
