package emu.grasscutter.game.ability;

import static org.junit.jupiter.api.Assertions.*;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public final class PredicateEvaluatorTest {
    private final GameEntity target =
            new GameEntity(null) {
                @Override public void initAbilities() {}
                @Override public int getEntityTypeId() { return 0; }
                @Override public Int2FloatMap getFightProperties() { return new Int2FloatOpenHashMap(); }
                @Override public Position getPosition() { return new Position(); }
                @Override public Position getRotation() { return new Position(); }
                @Override public SceneEntityInfo toProto() { return SceneEntityInfo.getDefaultInstance(); }
            };

    private boolean check(String comparison, Object value) {
        return PredicateEvaluator.evaluate(
                Map.of("$type", "ByTargetGlobalValue", "key", "count",
                        "compareType", comparison, "value", value),
                null, target, target, null);
    }

    @Test
    public void absentValueIsDifferentFromExplicitZero() {
        assertTrue(check("NoneOrEqual", 3f));
        target.getGlobalAbilityValues().put("count", 0f);
        assertFalse(check("NoneOrEqual", 3f));
        assertTrue(check("NoneOrEqual", 0f));
    }

    @Test
    public void lessAndEqualIncludesValuesBelowTheBound() {
        target.getGlobalAbilityValues().put("count", 2f);
        assertTrue(check("LessAndEqual", 3f));
        assertTrue(check("LessAndEqual", 2f));
        assertFalse(check("LessAndEqual", 1f));
    }

    @Test
    public void betweenUsesBothInclusiveBoundsAndGlobalValueNames() {
        target.getGlobalAbilityValues().put("minimum", 2f);
        target.getGlobalAbilityValues().put("maximum", 4f);
        var predicate = Map.<String, Object>of(
                "$type", "ByTargetGlobalValue",
                "key", "count",
                "compareType", "Between",
                "value", "minimum",
                "maxValue", "maximum");
        for (float value : new float[] {2f, 3f, 4f}) {
            target.getGlobalAbilityValues().put("count", value);
            assertTrue(PredicateEvaluator.evaluate(predicate, null, target, target, null));
        }
        for (float value : new float[] {1f, 5f}) {
            target.getGlobalAbilityValues().put("count", value);
            assertFalse(PredicateEvaluator.evaluate(predicate, null, target, target, null));
        }
    }

    @Test
    public void obfuscatedGlobalValuePredicateDoesNotPassUnconditionally() {
        target.getGlobalAbilityValues().put("count", 2f);
        assertFalse(PredicateEvaluator.evaluate(
                Map.of("$type", "EOFDCELPGFO", "key", "count", "value", 3f),
                null, target, target, null));
    }
}
