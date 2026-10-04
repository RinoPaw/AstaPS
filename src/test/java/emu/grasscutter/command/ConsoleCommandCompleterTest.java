package emu.grasscutter.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.jline.reader.Candidate;
import org.jline.reader.ParsedLine;
import org.junit.jupiter.api.Test;

public final class ConsoleCommandCompleterTest {
    @Command(label = "weather", aliases = {"w"})
    private static final class WeatherHandler implements CommandHandler {}

    @Test
    public void completesLabelsAliasesAndTarget() {
        var commandMap = new CommandMap(false);
        commandMap.registerCommand("weather", new WeatherHandler());
        var completer = new ConsoleCommandCompleter(commandMap);

        assertEquals(List.of("target", "w", "weather"), complete(completer, 0));
    }

    @Test
    public void doesNotCompleteCommandArguments() {
        var commandMap = new CommandMap(false);
        commandMap.registerCommand("weather", new WeatherHandler());
        var completer = new ConsoleCommandCompleter(commandMap);

        assertEquals(List.of(), complete(completer, 1));
    }

    private static List<String> complete(ConsoleCommandCompleter completer, int wordIndex) {
        var candidates = new ArrayList<Candidate>();
        completer.complete(null, new TestParsedLine(wordIndex), candidates);
        return candidates.stream().map(Candidate::value).toList();
    }

    private record TestParsedLine(int wordIndex) implements ParsedLine {
        @Override
        public String word() {
            return "";
        }

        @Override
        public int wordCursor() {
            return 0;
        }

        @Override
        public List<String> words() {
            return List.of();
        }

        @Override
        public String line() {
            return "";
        }

        @Override
        public int cursor() {
            return 0;
        }
    }
}
