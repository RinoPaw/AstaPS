package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.SpecialEnergyBarHelper;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;

@AbilityAction(AbilityModifierAction.Type.ChangePlayMode)
public final class ActionChangePlayMode extends AbilityActionHandler {
    private static final String NYX_INSTANT_KEY = "_ABILITY_NyxInstant_Active";

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        // ChangePlayMode is also used by fishing, aiming, minigames and many activity states. Those
        // must not create a Nightsoul marker or special-energy bar just because they share this action.
        if (action.toPlayMode == null || !action.toPlayMode.startsWith("NyxState")) {
            return true;
        }

        GameEntity resolvedTarget = target != null ? target : ability != null ? ability.getOwner() : null;
        if (!(resolvedTarget instanceof EntityAvatar avatar)) {
            return true;
        }

        var values = avatar.getGlobalAbilityValues();
        if (values != null) {
            values.put(NYX_INSTANT_KEY, 1.0f);
            avatar.onAbilityValueUpdate();
            if (avatar.getScene() != null && avatar.getScene().getHost() != null) {
                avatar.getScene()
                        .getHost()
                        .sendPacket(
                                new PacketServerGlobalValueChangeNotify(
                                        avatar, NYX_INSTANT_KEY, 1.0f));
            }
        }

        try {
            SpecialEnergyBarHelper.ensureAndSync(avatar);
        } catch (Throwable throwable) {
            Grasscutter.getLogger().debug("ChangePlayMode special bar sync failed", throwable);
        }
        return true;
    }
}
