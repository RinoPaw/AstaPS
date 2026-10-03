package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;

@AbilityMixin(value = AbilityMixinData.Type.ReviveElemEnergyMixin)
public class ReviveElemEnergyMixin extends AbilityMixinHandler {

    @Override
    public boolean execute(
            Ability ability, AbilityMixinData mixinData, ByteString abilityData, GameEntity target) {
        if (!(target instanceof EntityAvatar avatar)) {
            return false;
        }

        float baseEnergy = mixinData.baseEnergy.get(ability);
        float ratio = mixinData.ratio.get(ability);
        float amount = baseEnergy * ratio;

        // ReviveElemEnergy is a flat ability-driven change, so Energy Recharge must not scale it.
        // Some activity configs intentionally carry negative baseEnergy; never let those underflow
        // the elemental energy bar below zero.
        var elementType = avatar.getAvatar().getSkillDepot().getElementType();
        float currentEnergy = avatar.getFightProperty(elementType.getCurEnergyProp());
        amount = Math.max(amount, -currentEnergy);

        avatar.addEnergy(
                amount,
                PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,
                true);
        Grasscutter.getLogger()
                .debug(
                        "Changed avatar energy (mixin) by {} (base={}, ratio={})",
                        amount,
                        baseEnergy,
                        ratio);
        return true;
    }
}
