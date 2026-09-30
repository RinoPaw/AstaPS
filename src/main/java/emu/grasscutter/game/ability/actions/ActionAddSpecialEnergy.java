package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.NyxHelper;
import emu.grasscutter.game.ability.SkirkCunningBridge;
import emu.grasscutter.game.ability.SkirkCunningHelper;
import emu.grasscutter.game.ability.SpecialEnergyBarHelper;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import java.util.List;

@AbilityAction(AbilityModifierAction.Type.AddSpecialEnergy)
public final class ActionAddSpecialEnergy extends AbilityActionHandler {
    private static final String BURST_ENERGY_KEY = "_ABILITY_Mavuika_BurstEnergy";
    private static final String C2_EXTRA_ENERGY_KEY = "SkirkNew_Constellation_2_ExtraEnergy";

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        GameEntity entity = target != null ? target : ability.getOwner();
        if (entity == null) {
            return false;
        }

        SkirkCunningHelper.ensureC2ExtraEnergySpecial(ability, entity);
        SkirkCunningHelper.ensurePickableEnergyReviveSpecial(ability, entity);

        var properties = propertiesFor(ability, entity);
        float amount = action.ratio != null ? action.ratio.get(properties, 0.0f) : 0.0f;
        if (amount == 0.0f && action.amount != null) {
            amount = action.amount.get(properties, 0.0f);
        }

        var abilitySpecials = ability.getAbilitySpecials();
        if (Math.abs(amount) < 0.01f
                && SkirkCunningHelper.isSkirk(entity)
                && isExtraEnergyRatio(action.ratio)
                && abilitySpecials.containsKey(C2_EXTRA_ENERGY_KEY)) {
            float extraEnergy = abilitySpecials.getFloat(C2_EXTRA_ENERGY_KEY);
            if (extraEnergy >= 9.5f) {
                amount = extraEnergy;
            }
        }

        // Rift energy: the config has ratio=0 with value=SkirkNew_Pickable_Energy_Revive, and Gson
        // keeps only ratio, so the value is resolved explicitly here.
        if (Math.abs(amount) < 0.01f && SkirkCunningHelper.isSkirk(entity)) {
            float revive = SkirkCunningHelper.resolvePickableEnergyRevive(ability);
            if (revive >= 0.5f) {
                amount = revive;
            } else if (SkirkCunningHelper.isPickableEnergyReviveRatio(action.ratio)
                    && abilitySpecials.containsKey(SkirkCunningHelper.PICKABLE_ENERGY_REVIVE_KEY)) {
                float patched =
                        abilitySpecials.getFloat(SkirkCunningHelper.PICKABLE_ENERGY_REVIVE_KEY);
                if (patched >= 0.5f) {
                    amount = patched;
                }
            }
        }

        boolean hasConfiguredAmount =
                (action.ratio != null && action.ratio.isDynamic())
                        || (action.amount != null && action.amount.isDynamic())
                        || (action.ratio != null && action.ratio.getConstant() != 0.0f)
                        || (action.amount != null && action.amount.getConstant() != 0.0f);

        // The Mavuika-style default of +1.5 must not be applied to Skirk's rift absorb, whose ratio
        // is deliberately 0.
        if (amount == 0.0f
                && !hasConfiguredAmount
                && !SkirkCunningHelper.isPickableEnergyAbility(ability)) {
            amount = 1.5f;
        }

        if (NyxHelper.isSkirkEntity(entity) && entity instanceof EntityAvatar skirk) {
            Player player = ability.getPlayerOwner();
            if (player == null && entity.getScene() != null) {
                player = entity.getScene().getHost();
            }

            // Rift energy can also arrive via a GV; dedupe per absorb so only one +8 is granted.
            if (amount > 0.5f
                    && amount < 35.0f
                    && SkirkCunningHelper.isPickableEnergyAbility(ability)
                    && !SkirkCunningHelper.tryMarkPickableGrant(entity.getId())) {
                return true;
            }
            SkirkCunningBridge.applyDelta(player, skirk, amount);
            return true;
        }

        if (SkirkCunningHelper.tryDebouncedSkillGain(entity, amount)) {
            syncMavuikaBurst(entity);
            return true;
        }

        SpecialEnergyBarHelper.ensureAndSync(entity);
        entity.addSpecialEnergy(amount);
        SpecialEnergyBarHelper.ensureAndSync(entity);
        syncMavuikaBurst(entity);
        return true;
    }

    private static boolean isExtraEnergyRatio(DynamicFloat dynamicFloat) {
        if (dynamicFloat == null || !dynamicFloat.isDynamic()) {
            return false;
        }
        try {
            List<DynamicFloat.StackOp> ops = dynamicFloat.getOps();
            if (ops == null) {
                return false;
            }
            for (var op : ops) {
                if (op != null && C2_EXTRA_ENERGY_KEY.equals(op.sValue)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void syncMavuikaBurst(GameEntity entity) {
        float energy = entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY);
        entity.getGlobalAbilityValues().put(BURST_ENERGY_KEY, energy);
        entity.onAbilityValueUpdate();
        if (entity.getScene() != null && entity.getScene().getHost() != null) {
            entity.getScene()
                    .getHost()
                    .sendPacket(
                            new PacketServerGlobalValueChangeNotify(
                                    entity, BURST_ENERGY_KEY, energy));
        }
    }
}
