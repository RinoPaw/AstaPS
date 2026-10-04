package emu.grasscutter.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jline.reader.Candidate;
import org.jline.reader.ParsedLine;
import org.junit.jupiter.api.Test;

public final class ConsoleCommandCompleterTest {
    @Test
    public void completesLabelsAliasesAndTarget() {
        var completer =
                new ConsoleCommandCompleter(
                        Map.of("weather", List.of("<weatherId>"), "w", List.of("<weatherId>")));

        assertEquals(
                List.of("target", "w", "weather"),
                complete(completer, List.of(""), 0, ""));
    }

    @Test
    public void normalizesAndDeduplicatesCommandNames() {
        var completer =
                new ConsoleCommandCompleter(
                        Map.of(
                                "GameSpeed",
                                List.of("<0.1|0.2>"),
                                "speed",
                                List.of("<0.1|0.2>"),
                                "SPEED",
                                List.of("<0.1|0.2>")));

        assertEquals(
                List.of("gamespeed", "speed", "target"),
                complete(completer, List.of(""), 0, ""));
    }

    @Test
    public void completesSubcommandsFromUsage() {
        var completer =
                new ConsoleCommandCompleter(
                        Map.of(
                                "debug",
                                List.of(
                                        "abilities <entityId> [config]",
                                        "entity",
                                        "setvar <entityId> <varName> <floatValue> [abilityIdx]",
                                        "dynamicmap")));

        assertEquals(
                List.of("abilities", "dynamicmap", "entity", "setvar"),
                complete(completer, List.of("debug", ""), 1, ""));
    }

    @Test
    public void completesEnumeratedArguments() {
        var completer =
                new ConsoleCommandCompleter(
                        Map.of(
                                "barrier",
                                List.of("[on|off|1|0]"),
                                "speed",
                                List.of("<0.1|0.2|0.5|1.0>")));

        assertEquals(
                List.of("0", "1", "off", "on"),
                complete(completer, List.of("barrier", ""), 1, ""));
        assertEquals(
                List.of("0.1", "0.2", "0.5", "1.0"),
                complete(completer, List.of("speed", ""), 1, ""));
    }

    @Test
    public void doesNotGuessFreeFormArguments() {
        var completer =
                new ConsoleCommandCompleter(Map.of("weather", List.of("<weatherId> [<climateType>]")));

        assertEquals(
                List.of(), complete(completer, List.of("weather", ""), 1, ""));
    }

    @Test
    public void targetArgumentsDoNotShiftUsagePosition() {
        var completer =
                new ConsoleCommandCompleter(
                        Map.of("debug", List.of("abilities <entityId>", "entity")));

        assertEquals(
                List.of("abilities", "entity"),
                complete(completer, List.of("debug", "@10001", ""), 2, ""));
    }

    @Test
    public void doesNotCompleteTargetValue() {
        var completer =
                new ConsoleCommandCompleter(Map.of("weather", List.of("<weatherId>")));

        assertEquals(
                List.of(), complete(completer, List.of("weather", "@10"), 1, "@10"));
    }

    private static List<String> complete(
            ConsoleCommandCompleter completer, List<String> words, int wordIndex, String word) {
        var candidates = new ArrayList<Candidate>();
        completer.complete(null, new TestParsedLine(words, wordIndex, word), candidates);
        return candidates.stream().map(Candidate::value).toList();
    }

    private record TestParsedLine(List<String> words, int wordIndex, String word)
            implements ParsedLine {
        @Override
        public int wordCursor() {
            return word.length();
        }

        @Override
        public String line() {
            return String.join(" ", words);
        }

        @Override
        public int cursor() {
            return line().length();
        }
    }
}
