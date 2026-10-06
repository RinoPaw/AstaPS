package emu.grasscutter.game.ability;

import static org.junit.jupiter.api.Assertions.*;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.utils.JsonUtils;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class ParticleAbilityLookupTest {
    @Test
    public void uninstancedDynamicSkillStillResolvesItsParticleAction() {
        var skill = ability("Avatar_Test_ElementalArt", "GenerateElemBall");
        assertTrue(skill.localIdToAction.isEmpty());
        assertSame(skill, AbilityManager.findElemBallAbilityData(List.of(skill), "Avatar_Test_", 513));
    }

    @Test
    public void ambiguousAndUnrelatedParticleActionsAreNotGuessed() {
        var skill = ability("Avatar_Test_ElementalArt", "GenerateElemBall");
        var second = ability("Avatar_Test_ElementalBurst", "GenerateElemBall");
        var otherAvatar = ability("Avatar_Other_ElementalArt", "GenerateElemBall");
        var nonParticle = ability("Avatar_Test_Passive", "HealHP");
        assertNull(AbilityManager.findElemBallAbilityData(List.of(skill, second), "Avatar_Test_", 513));
        assertNull(AbilityManager.findElemBallAbilityData(List.of(otherAvatar, nonParticle), "Avatar_Test_", 513));
        assertSame(skill, AbilityManager.findElemBallAbilityData(
                List.of(otherAvatar, nonParticle, skill), "Avatar_Test_", 513));
    }

    private static AbilityData ability(String name, String action) {
        return JsonUtils.decode(
                "{\"abilityName\":\"" + name + "\",\"isDynamicAbility\":true,"
                        + "\"onAdded\":[{\"$type\":\"" + action + "\"}]}",
                AbilityData.class);
    }
}
