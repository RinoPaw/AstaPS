package emu.grasscutter.command;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

/** Completes server-console command labels and aliases. */
public final class ConsoleCommandCompleter implements Completer {
    private final Supplier<? extends Collection<String>> commandNamesSupplier;

    public ConsoleCommandCompleter() {
        this(ConsoleCommandCompleter::registeredCommandNames);
    }

    ConsoleCommandCompleter(Collection<String> commandNames) {
        this(() -> commandNames);
    }

    private ConsoleCommandCompleter(Supplier<? extends Collection<String>> commandNamesSupplier) {
        this.commandNamesSupplier = commandNamesSupplier;
    }

    private static Collection<String> registeredCommandNames() {
        CommandMap commandMap = CommandMap.getInstance();
        return commandMap == null ? List.of() : commandMap.getAnnotations().keySet();
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        if (line.wordIndex() != 0) return;

        var names = new TreeSet<String>();
        commandNamesSupplier.get().stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .forEach(names::add);
        names.add("target");

        for (String name : names) {
            candidates.add(new Candidate(name));
        }
    }
}
