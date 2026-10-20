package emu.grasscutter.game.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resource paths and template arguments have explicit, structurally distinct variable references. */
final class RewardResourceExpression {
    private record Reference(int start, int end, String name, Integer index) {}

    private final String source;
    private final boolean table;
    private final List<Reference> references;

    private RewardResourceExpression(String source, boolean table, List<Reference> references) {
        this.source = source;
        this.table = table;
        this.references = List.copyOf(references);
    }

    static RewardResourceExpression parse(String source, boolean table) {
        var references = new ArrayList<Reference>();
        boolean arguments = false, closed = false;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (closed) throw invalid(source);
            if (ch == '<') {
                // At path level, '<' after a name opens a template's argument list.
                // Inside that list, '<name>' is always a variable reference.
                if (table && !arguments && i > 0 && source.charAt(i - 1) != '.') {
                    arguments = true;
                    continue;
                }
                int end = source.indexOf('>', i + 1);
                if (end < 0) throw invalid(source);
                String name = source.substring(i + 1, end);
                if (!name.matches("[a-zA-Z_][a-zA-Z0-9_-]*")) throw invalid(source);
                Integer index = null;
                int next = end + 1;
                if (next < source.length() && source.charAt(next) == '[') {
                    int close = source.indexOf(']', next + 1);
                    if (close < 0 || !source.substring(next + 1, close).matches("[0-9]+")) throw invalid(source);
                    try {
                        index = Integer.valueOf(source.substring(next + 1, close));
                    } catch (NumberFormatException ex) {
                        throw invalid(source);
                    }
                    next = close + 1;
                }
                references.add(new Reference(i, next, name, index));
                i = next - 1;
            } else if (ch == '>') {
                if (!arguments) throw invalid(source);
                arguments = false;
                closed = true;
            } else if ((ch == ',' && !arguments) || ch == '[' || ch == ']') {
                throw invalid(source);
            }
        }
        if (arguments) throw invalid(source);
        return new RewardResourceExpression(source, table, references);
    }

    private static IllegalArgumentException invalid(String source) {
        return new IllegalArgumentException("Invalid reward resource expression: " + source);
    }

    boolean hasVariables() { return !references.isEmpty(); }

    void requireDeclared(Set<String> declarations) {
        for (var reference : references) {
            if (!declarations.contains(reference.name)) {
                throw new IllegalArgumentException("Undeclared reward variable <" + reference.name + "> in " + source);
            }
        }
    }

    List<String> expand(Map<String, List<String>> bindings) {
        List<String> expanded = List.of("");
        int cursor = 0;
        for (var reference : references) {
            List<String> bound = bindings.get(reference.name);
            if (bound == null) {
                throw new IllegalArgumentException("Undeclared reward variable <" + reference.name + "> in " + source);
            }
            // A declaration in another selector provides no value for this object.
            if (bound.isEmpty()) return List.of();
            List<String> choices;
            if (reference.index != null) {
                if (reference.index >= bound.size()) {
                    throw new IllegalArgumentException("Reward binding index out of range: " + source);
                }
                choices = List.of(bound.get(reference.index));
            } else {
                if (table && bound.size() != 1) {
                    throw new IllegalArgumentException("List binding requires an explicit index: " + source);
                }
                choices = bound;
            }
            var next = new ArrayList<String>();
            for (String prefix : expanded) {
                for (String choice : choices) {
                    next.add(prefix + source.substring(cursor, reference.start) + choice);
                }
            }
            expanded = next;
            cursor = reference.end;
        }
        String suffix = source.substring(cursor);
        return expanded.stream().map(prefix -> prefix + suffix).toList();
    }
}
