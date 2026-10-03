package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.ArlecchinoBurstBoL;
import emu.grasscutter.game.ability.MavuikaSpiritHelper;
import emu.grasscutter.game.ability.XilonenC6HealHelper;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;

/** Writes an ability global value, honoring its configured limit range when enabled. */
@AbilityAction({
    AbilityModifierAction.Type.SetGlobalValue,
    AbilityModifierAction.Type.SetGlobalValueV2
})
public final class ActionSetGlobalValue extends AbilityActionHandler {

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        var properties = propertiesFor(ability);

        var valueKey = action.key;
        float computedValue = action.writtenValue().get(properties, 0f);
        if (action.useLimitRange) {
            float minValue = action.minValue.get(properties, 0f);
            float maxValue = action.maxValue.get(properties, 0f);
            computedValue = Math.max(minValue, Math.min(computedValue, maxValue));
        }

        Player owner = ability != null ? ability.getPlayerOwner() : null;
        if (owner == null && target != null && target.getScene() != null) {
            owner = target.getScene().getHost();
        }
        // Mavuika: Nightsoul spend converting to Fighting Spirit, and the C2 blessing state, both
        // need the old value read before the put.
        if ("NyxValue".equals(valueKey)
                || "_ABILITY_Mavuika_IsNyxState".equals(valueKey)
                || "_ABILITY_Mavuika_IsSpecialMove".equals(valueKey)) {
            try {
                MavuikaSpiritHelper.beforeGlobalFloatPut(owner, target, valueKey, computedValue);
            } catch (Throwable ignored) {
            }
        }
        if ("NyxValue".equals(valueKey) && target != null) {
            Float oldNyx = target.getGlobalAbilityValues().get(valueKey);
            if (oldNyx != null && computedValue < oldNyx) {
                emu.grasscutter.game.ability.NightsoulStaminaExempt.markNyxCostActive(target);
            }
        }
        // Highest priority: Arlecchino Q - pre-arm then refuse zeroing Cur_HPDebts.
        if (("Cur_HPDebts".equals(valueKey)
                        || "_HPDebts".equals(valueKey)
                        || "_ABILITY_Cur_HPDebts".equals(valueKey))
                && computedValue <= 0.5f
                && target instanceof EntityAvatar av
                && av.getAvatar() != null
                && av.getAvatar().getAvatarId() == 10000096) {
            ArlecchinoBurstBoL.tryPreArmFromAbility(ability, av);
            // Any wipe of ability globals for Arlecchino BoL during or around Q - lock and repin.
            if (!ArlecchinoBurstBoL.isConsumeBlocked(av)) {
                float cur = av.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
                if (cur > 0.5f) {
                    ArlecchinoBurstBoL.onBurstCast(av);
                }
            }
            if (ArlecchinoBurstBoL.isConsumeBlocked(av)) {
                ArlecchinoBurstBoL.repinClientBoL(av);
                emu.grasscutter.Grasscutter.getLogger()
                        .info(
                                "[BoL] skip SetGlobalValue {}={} (consume-lock)",
                                valueKey,
                                computedValue);
                return true;
            }
        }
        target.getGlobalAbilityValues().put(valueKey, computedValue);
        target.onAbilityValueUpdate();
        if ("_ABILITY_Xilonen_Constellation_6_IsShowTime".equals(valueKey)) {
            XilonenC6HealHelper.onGlobalFloat(ability.getPlayerOwner(), target, valueKey, computedValue);
        }
        if ("_ABILITY_Avatar_ForbidFoodHeal".equals(valueKey)
                && target instanceof EntityAvatar avatar
                && avatar.getAvatar().getAvatarId() == 10000096) {
            target.setConvertToHpDebt(computedValue > 0f);
        }

        // Team abilities run against a pseudo-entity that is in no scene.
        if (!AbilityManager.isServerOwnedChain()) {
            var scene = target.getScene();
            var host = scene == null ? null : scene.getHost();
            if (host != null) {
                host.sendPacket(
                        new PacketServerGlobalValueChangeNotify(target, valueKey, computedValue));
            }
        }
        return true;
    }
}
