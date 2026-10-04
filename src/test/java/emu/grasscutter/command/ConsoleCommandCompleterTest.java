package emu.grasscutter.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.jline.reader.Candidate;
import org.jline.reader.ParsedLine;
import org.junit.jupiter.api.Test;

public final class ConsoleCommandCompleterTest {
    @Test
    public void completesLabelsAliasesAndTarget() {
        var completer = new ConsoleCommandCompleter(List.of("weather", "w"));

        assertEquals(List.of("target", "w", "weather"), complete(completer, 0));
    }

    @Test
    public void normalizesAndDeduplicatesCommandNames() {
        var completer = new ConsoleCommandCompleter(List.of("GameSpeed", "speed", "SPEED"));

        assertEquals(List.of("gamespeed", "speed", "target"), complete(completer, 0));
    }

    @Test
    public void doesNotCompleteCommandArguments() {
        var completer = new ConsoleCommandCompleter(List.of("weather", "w"));

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
