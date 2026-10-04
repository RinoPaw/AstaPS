package emu.grasscutter.command;

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
    private final Supplier<CommandMap> commandMapSupplier;

    public ConsoleCommandCompleter() {
        this(CommandMap::getInstance);
    }

    ConsoleCommandCompleter(CommandMap commandMap) {
        this(() -> commandMap);
    }

    private ConsoleCommandCompleter(Supplier<CommandMap> commandMapSupplier) {
        this.commandMapSupplier = commandMapSupplier;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        if (line.wordIndex() != 0) return;

        CommandMap commandMap = commandMapSupplier.get();
        if (commandMap == null) return;

        var names = new TreeSet<String>();
        commandMap.getAnnotations().keySet().stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .forEach(names::add);
        names.add("target");

        for (String name : names) {
            candidates.add(new Candidate(name));
        }
    }
}
