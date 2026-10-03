package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.MavuikaSpiritHelper;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;

@AbilityAction(AbilityModifierAction.Type.NyxSet)
public final class ActionNyxSet extends AbilityActionHandler {
    private static final String NYX_KEY = "NyxValue";

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        GameEntity entity = target != null ? target : ability.getOwner();
        if (entity == null) {
            return false;
        }

        var properties = propertiesFor(ability, entity);
        var globals = entity.getGlobalAbilityValues();
        globals.forEach(
                (key, value) -> {
                    if (key != null && value != null) {
                        properties.put(key, value.floatValue());
                    }
                });

        float value = action.writtenValue().get(properties, 0.0f);
        float max = resolveBound(properties, action.maxValue, "NyxValueMax", 120.0f);
        float min = resolveBound(properties, action.minValue, "NyxValueMin", 0.0f);
        value = Math.max(min, Math.min(max, value));

        if (ability.getPlayerOwner() != null) {
            MavuikaSpiritHelper.beforeGlobalFloatPut(
                    ability.getPlayerOwner(), entity, NYX_KEY, value);
        }

        globals.put(NYX_KEY, value);
        entity.onAbilityValueUpdate();
        if (entity.getScene() != null && entity.getScene().getHost() != null) {
            entity.getScene()
                    .getHost()
                    .sendPacket(new PacketServerGlobalValueChangeNotify(entity, NYX_KEY, value));
        }
        return true;
    }

    private static float resolveBound(
            Object2FloatMap<String> properties,
            DynamicFloat dynamicFloat,
            String key,
            float fallback) {
        if (dynamicFloat != null) {
            float resolved = dynamicFloat.get(properties, Float.NaN);
            if (!Float.isNaN(resolved)) {
                return resolved;
            }
        }
        return properties.containsKey(key) ? properties.getFloat(key) : fallback;
    }
}
