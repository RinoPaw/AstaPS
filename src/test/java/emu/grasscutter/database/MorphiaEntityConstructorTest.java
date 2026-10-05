package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import emu.grasscutter.game.achievement.Achievement;
import emu.grasscutter.game.achievement.Achievements;
import emu.grasscutter.game.home.GameHome;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

class MorphiaEntityConstructorTest {
    @Test
    void loginEntitiesProvideNoArgConstructors() {
        assertNoArgConstructor(Achievements.class);
        assertNoArgConstructor(Achievement.class);
        assertNoArgConstructor(GameHome.class);
    }

    private static void assertNoArgConstructor(Class<?> type) {
        assertDoesNotThrow(
                () -> {
                    Constructor<?> constructor = type.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    constructor.newInstance();
                },
                () -> type.getName() + " must remain constructible by Morphia");
    }
}
