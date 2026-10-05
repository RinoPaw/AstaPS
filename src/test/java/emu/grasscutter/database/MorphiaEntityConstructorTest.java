package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.morphia.annotations.Entity;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;

class MorphiaEntityConstructorTest {
    @Test
    void allConcreteMorphiaEntitiesProvideNoArgConstructors() {
        var missingConstructors =
                new Reflections("emu.grasscutter")
                        .getTypesAnnotatedWith(Entity.class)
                        .stream()
                        .filter(type -> !type.isInterface())
                        .filter(type -> !Modifier.isAbstract(type.getModifiers()))
                        .filter(MorphiaEntityConstructorTest::hasNoArgConstructor)
                        .sorted(Comparator.comparing(Class::getName))
                        .map(Class::getName)
                        .toList();

        assertTrue(
                missingConstructors.isEmpty(),
                () ->
                        "Morphia 2.5 requires constructible mapped entities; missing no-arg constructor(s): "
                                + String.join(", ", missingConstructors));
    }

    private static boolean hasNoArgConstructor(Class<?> type) {
        try {
            type.getDeclaredConstructor();
            return false;
        } catch (NoSuchMethodException ignored) {
            return true;
        }
    }
}
