package emu.grasscutter.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

/** Completes server-console command labels, aliases, subcommands, and enumerated arguments. */
public final class ConsoleCommandCompleter implements Completer {
    private final Supplier<? extends Map<String, List<String>>> commandSyntaxSupplier;

    public ConsoleCommandCompleter() {
        this(ConsoleCommandCompleter::registeredCommandSyntax);
    }

    ConsoleCommandCompleter(Map<String, List<String>> commandSyntax) {
        this(() -> commandSyntax);
    }

    private ConsoleCommandCompleter(
            Supplier<? extends Map<String, List<String>>> commandSyntaxSupplier) {
        this.commandSyntaxSupplier = commandSyntaxSupplier;
    }

    private static Map<String, List<String>> registeredCommandSyntax() {
        CommandMap commandMap = CommandMap.getInstance();
        if (commandMap == null) return Map.of();

        var syntax = new LinkedHashMap<String, List<String>>();
        for (var entry : commandMap.getAnnotations().entrySet()) {
            syntax.put(entry.getKey(), List.of(entry.getValue().usage()));
        }
        return syntax;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        Map<String, List<String>> syntax = commandSyntaxSupplier.get();
        if (line.wordIndex() == 0) {
            completeCommandNames(syntax.keySet(), candidates);
            return;
        }

        List<String> words = line.words();
        if (words.isEmpty() || line.word().startsWith("@")) return;

        String commandName = words.get(0).toLowerCase(Locale.ROOT);
        List<String> usages = syntax.get(commandName);
        if (usages == null) return;

        List<String> previousArgs = new ArrayList<>();
        for (int i = 1; i < line.wordIndex() && i < words.size(); i++) {
            String word = words.get(i);
            if (!word.startsWith("@")) previousArgs.add(word);
        }

        int argIndex = previousArgs.size();
        var values = new TreeSet<String>();
        for (String usage : usages) {
            addUsageCandidates(usage, argIndex, previousArgs, values);
        }
        for (String value : values) {
            candidates.add(new Candidate(value));
        }
    }

    private static void completeCommandNames(
            Collection<String> commandNames, List<Candidate> candidates) {
        var names = new TreeSet<String>();
        commandNames.stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .forEach(names::add);
        names.add("target");

        for (String name : names) {
            candidates.add(new Candidate(name));
        }
    }

    private static void addUsageCandidates(
            String usage, int argIndex, List<String> previousArgs, Collection<String> candidates) {
        if (usage == null || usage.isBlank()) return;

        String[] tokens = usage.trim().split("\\s+");
        if (argIndex >= tokens.length) return;

        for (int i = 0; i < argIndex; i++) {
            List<String> literals = literalsForToken(tokens[i]);
            String previousArg = previousArgs.get(i);
            if (!literals.isEmpty()
                    && literals.stream().noneMatch(value -> value.equalsIgnoreCase(previousArg))) {
                return;
            }
        }

        candidates.addAll(literalsForToken(tokens[argIndex]));
    }

    private static List<String> literalsForToken(String token) {
        if (token == null || token.isBlank()) return List.of();

        String value = token.trim();
        if (!value.contains("|")) {
            if (startsWithSyntaxWrapper(value)) return List.of();
            return isSafeLiteral(value) ? List.of(value) : List.of();
        }

        value = stripGroupWrappers(value);
        if (isWholeAngleGroup(value)) {
            value = value.substring(1, value.length() - 1).trim();
        }

        return Arrays.stream(value.split("\\|"))
                .map(ConsoleCommandCompleter::stripGroupWrappers)
                .filter(part -> !(part.startsWith("<") && part.endsWith(">")))
                .filter(ConsoleCommandCompleter::isSafeLiteral)
                .toList();
    }

    private static boolean startsWithSyntaxWrapper(String value) {
        return value.startsWith("[") || value.startsWith("<") || value.startsWith("(");
    }

    private static String stripGroupWrappers(String value) {
        String result = value.trim();
        boolean changed;
        do {
            changed = false;
            if (result.length() >= 2
                    && ((result.startsWith("[") && result.endsWith("]"))
                            || (result.startsWith("(") && result.endsWith(")")))) {
                result = result.substring(1, result.length() - 1).trim();
                changed = true;
            }
        } while (changed);
        return result;
    }

    private static boolean isWholeAngleGroup(String value) {
        return value.length() >= 2
                && value.startsWith("<")
                && value.endsWith(">")
                && value.indexOf('>') == value.length() - 1;
    }

    private static boolean isSafeLiteral(String value) {
        return !value.isBlank()
                && !value.contains("<")
                && !value.contains(">")
                && !value.contains("[")
                && !value.contains("]")
                && !value.contains("(")
                && !value.contains(")")
                && !value.contains("...");
    }
}
