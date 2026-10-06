package emu.grasscutter.game.ability.actions;

import static org.junit.jupiter.api.Assertions.*;
import emu.grasscutter.data.common.DynamicFloat;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import org.junit.jupiter.api.Test;

public final class ActionGenerateElemBallTest {
    @Test
    public void unresolvedElementRatioStillGeneratesParticles() {
        var specials = new Object2FloatOpenHashMap<String>();
        assertEquals(
                1f,
                ActionGenerateElemBall.ratioOf(
                        new DynamicFloat("Skill_GetElementRatio"), specials));
        assertEquals(
                0f,
                ActionGenerateElemBall.ratioOf(new DynamicFloat("OtherMissingValue"), specials));
    }
}
