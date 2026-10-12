package emu.grasscutter.data.binout;

import static org.junit.jupiter.api.Assertions.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.binout.config.ConfigEntityBase;
import emu.grasscutter.data.binout.config.fields.ConfigCombatSummon;
import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.Test;

public final class AbilityCombatNamesTest {
    @Test
    public void obfuscatedCallbacksKeepTheirActionLocalIds() {
        var ability = JsonUtils.decode(
                "{\"abilityName\":\"Avatar_Test\",\"PFJMEMFDGOH\":[{\"$type\":\"HealHP\"}],"
                        + "\"BJFNHPKOOLF\":[{\"$type\":\"KillSelf\"}],"
                        + "\"KHCFCFACICO\":[{\"$type\":\"SetGlobalValue\"}],"
                        + "\"modifiers\":{\"Default\":{\"OOINAHKLNHJ\":[{\"$type\":\"HealHP\"}]}}}",
                AbilityData.class);
        ability.initialize();
        assertEquals(1, ability.onFieldEnter.length);
        assertEquals(1, ability.onExit.length);
        assertEquals(1, ability.onAttach.length);
        assertEquals(1, ability.modifiers.get("Default").onChangeAuthority.length);
        assertEquals(4, ability.localIdToAction.size());
    }

    @Test
    public void obfuscatedHealingAndSummonFieldsAreRead() {
        var action = JsonUtils.decode(
                "{\"$type\":\"HealHP\",\"FFNEJGGNAFF\":\"Arlecchino_ElementalBurst_Heal\","
                        + "\"GJBFAJMJFOP\":0.2,\"BJEKIJMNDAA\":0.3,\"FPOCDLCHDPE\":0.4,"
                        + "\"KHFHMJCMOHH\":0.5,\"BPFPAELLNIL\":true,\"PCLFAKBGHCI\":7,"
                        + "\"NPNEPKILMEK\":[1,2]}",
                AbilityModifierAction.class);
        assertEquals("Arlecchino_ElementalBurst_Heal", action.healTag);
        assertEquals(0.2f, action.amountByCasterMaxHPRatio.get());
        assertEquals(0.3f, action.amountByTargetCurrentHPRatio.get());
        assertEquals(0.4f, action.amountByTargetMaxHPRatio.get());
        assertEquals(0.5f, action.limboByTargetMaxHPRatio.get());
        assertTrue(action.ignoreAbilityProperty);
        assertEquals(7, action.summonTag);
        assertArrayEquals(new int[] {1, 2}, action.callParamList);
        assertEquals(AbilityModifierAction.Type.ByTargetGlobalValue,
                JsonUtils.decode("{\"$type\":\"EOFDCELPGFO\"}", AbilityModifierAction.class).type);
    }

    @Test
    public void obfuscatedMixinActionsAndEntityStateAreRead() {
        var mixin = JsonUtils.decode(
                "{\"$type\":\"GOBNKFIFGFJ\",\"HMBEKPDBCEK\":[{\"$type\":\"HealHP\"}],"
                        + "\"CMEPEHIJMPL\":[{\"$type\":\"SetGlobalValue\"}],"
                        + "\"IGAMNNAADJB\":[\"LunarBloom\"],\"DEFOFMOIAAI\":false}",
                AbilityMixinData.class);
        assertEquals(AbilityMixinData.Type.HJKDMEOOBDK, mixin.type);
        assertEquals(1, mixin.onTriggerUltimateSkill.length);
        assertEquals(1, mixin.succActions.length);
        assertEquals("LunarBloom", mixin.reactionTypes.get(0));
        assertFalse(mixin.removeAppliedModifier);

        var entity = JsonUtils.decode("{\"OCDDHEEDBBH\":{\"initServerGlobalValues\":{\"mark\":2.0}}}", ConfigEntityBase.class);
        assertEquals(2f, entity.getGlobalValue().getInitServerGlobalValues().get("mark").floatValue());

        var summon = JsonUtils.decode("{\"summonTags\":[{\"PCLFAKBGHCI\":7}]}", ConfigCombatSummon.class);
        assertEquals(7, summon.getSummonTags().get(0).getSummonTag());
    }
}
