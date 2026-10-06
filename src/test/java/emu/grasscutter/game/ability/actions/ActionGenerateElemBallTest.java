package emu.grasscutter.game.ability.actions;

import static org.junit.jupiter.api.Assertions.*;
import emu.grasscutter.data.common.DynamicFloat;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class ActionGenerateElemBallTest {
    @Test
    public void unresolvedElementRatioStillGeneratesParticles() {
        var specials = new Object2FloatOpenHashMap<String>();
        assertEquals(1f, ActionGenerateElemBall.ratioOf(new DynamicFloat("Skill_GetElementRatio"), specials));
        assertEquals(0f, ActionGenerateElemBall.ratioOf(new DynamicFloat("OtherMissingValue"), specials));
    }

    @Test
    public void configuredElementRatioAndFormulasArePreserved() {
        var specials = new Object2FloatOpenHashMap<String>();
        specials.put("Skill_GetElementRatio", 0f);
        assertEquals(0f, ActionGenerateElemBall.ratioOf(new DynamicFloat("Skill_GetElementRatio"), specials));
        specials.put("Skill_GetElementRatio", 0.5f);
        assertEquals(0.5f, ActionGenerateElemBall.ratioOf(new DynamicFloat("Skill_GetElementRatio"), specials));
        var expression = new DynamicFloat(List.of(
                new DynamicFloat.StackOp("Skill_GetElementRatio"),
                new DynamicFloat.StackOp(2f),
                new DynamicFloat.StackOp("MUL")));
        assertEquals(1f, ActionGenerateElemBall.ratioOf(expression, specials));
    }
}
