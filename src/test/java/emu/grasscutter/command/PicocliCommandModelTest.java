package emu.grasscutter.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;

public final class PicocliCommandModelTest {
    @Test
    @DisplayName("every command can build its picocli model")
    public void everyCommandBuildsCompletionModel() {
        var failures = new ArrayList<String>();
        var reflections = new Reflections("emu.grasscutter.command.commands");
        var commandTypes = new ArrayList<>(reflections.getTypesAnnotatedWith(Command.class));
        commandTypes.sort(Comparator.comparing(Class::getName));

        for (Class<?> commandType : commandTypes) {
            try {
                Object instance = commandType.getDeclaredConstructor().newInstance();
                if (!(instance instanceof CommandHandler handler)) {
                    failures.add(commandType.getName() + ": does not implement CommandHandler");
                    continue;
                }

                handler.createCompletionCommandLine();
            } catch (Throwable failure) {
                failures.add(commandType.getName() + ": " + failure);
            }
        }

        assertTrue(
                failures.isEmpty(),
                () -> "Invalid picocli command models:\n" + String.join("\n", failures));
    }
}
