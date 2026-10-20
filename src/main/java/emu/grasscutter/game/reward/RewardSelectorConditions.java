package emu.grasscutter.game.reward;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Compiles typed attribute conditions into ordinary CSS attribute selectors. */
final class RewardSelectorConditions {
    private static final String IDENTIFIER = "[A-Za-z_][A-Za-z0-9_-]*";
    private static final String NUMBER = "[+-]?[0-9]+(?:\\.[0-9]+)?";
    private static final Pattern MISSING = Pattern.compile("!\\s*(" + IDENTIFIER + ")");
    private static final Pattern RANGE = Pattern.compile(
            "(" + NUMBER + ")\\s*(<=|<)\\s*(" + IDENTIFIER + ")\\s*(<=|<)\\s*(" + NUMBER + ")");
    private static final Pattern MEMBER = Pattern.compile("(" + IDENTIFIER + ")\\s+in\\s*\\((.*)\\)", Pattern.DOTALL);
    private static final Pattern COMPARE = Pattern.compile("(" + IDENTIFIER + ")\\s*(<=|>=|!=|=|<|>)\\s*(.+)", Pattern.DOTALL);
    private static final String PREFIX = "data-astaps-condition-";

    record Rewrite(String css, Map<String, Condition> conditions) {}

    sealed interface Condition permits Missing, Comparison, Range, Member {
        boolean matches(Map<String, String> attributes);
    }

    private record Literal(String text, BigDecimal number) {
        boolean isNumber() { return number != null; }
        boolean equalTo(String actual) {
            if (actual == null) return false;
            if (!isNumber()) return text.equals(actual);
            try { return number.compareTo(new BigDecimal(actual)) == 0; }
            catch (NumberFormatException ex) { return false; }
        }
        int compareTo(String actual) {
            try { return new BigDecimal(actual).compareTo(number); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("Non-numeric attribute value: " + actual); }
        }
    }

    private record Missing(String name) implements Condition {
        public boolean matches(Map<String, String> attributes) { return !attributes.containsKey(name); }
    }

    private record Comparison(String name, String operator, Literal expected) implements Condition {
        public boolean matches(Map<String, String> attributes) {
            String actual = attributes.get(name);
            if (actual == null) return false;
            if (operator.equals("=")) return expected.equalTo(actual);
            if (operator.equals("!=")) return !expected.equalTo(actual)
                    && (!expected.isNumber() || numeric(actual));
            if (!expected.isNumber()) return false;
            try {
                int result = expected.compareTo(actual);
                return switch (operator) {
                    case "<" -> result < 0;
                    case "<=" -> result <= 0;
                    case ">" -> result > 0;
                    case ">=" -> result >= 0;
                    default -> throw new IllegalStateException("Invalid comparison operator: " + operator);
                };
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
    }

    private record Range(String name, Literal lower, boolean lowerInclusive,
                         Literal upper, boolean upperInclusive) implements Condition {
        public boolean matches(Map<String, String> attributes) {
            String actual = attributes.get(name);
            if (actual == null || !numeric(actual)) return false;
            int low = lower.compareTo(actual), high = upper.compareTo(actual);
            return (low > 0 || lowerInclusive && low == 0)
                    && (high < 0 || upperInclusive && high == 0);
        }
    }

    private record Member(String name, List<Literal> candidates) implements Condition {
        public boolean matches(Map<String, String> attributes) {
            String actual = attributes.get(name);
            return actual != null && candidates.stream().anyMatch(value -> value.equalTo(actual));
        }
    }

    private static boolean numeric(String text) {
        try { new BigDecimal(text); return true; }
        catch (NumberFormatException ex) { return false; }
    }

    private static Literal literal(String raw) {
        String value = raw.trim();
        if (value.length() >= 2 && (value.charAt(0) == '\'' || value.charAt(0) == '"')
                && value.charAt(value.length() - 1) == value.charAt(0)) {
            StringBuilder unquoted = new StringBuilder();
            for (int i = 1; i < value.length() - 1; i++) {
                if (value.charAt(i) == '\\' && i + 1 < value.length() - 1) i++;
                unquoted.append(value.charAt(i));
            }
            return new Literal(unquoted.toString(), null);
        }
        if (value.matches(NUMBER)) return new Literal(null, new BigDecimal(value));
        if (value.matches(IDENTIFIER)) return new Literal(value, null);
        throw new IllegalArgumentException("Invalid reward selector literal: " + raw);
    }

    private static List<Literal> literals(String source) {
        var result = new ArrayList<Literal>();
        int start = 0;
        char quote = 0;
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            if (quote != 0) {
                if (current == '\\') i++;
                else if (current == quote) quote = 0;
            } else if (current == '\'' || current == '"') quote = current;
            else if (current == ',') {
                result.add(literal(source.substring(start, i)));
                start = i + 1;
            }
        }
        if (quote != 0) throw new IllegalArgumentException("Unclosed selector literal: " + source);
        result.add(literal(source.substring(start)));
        if (result.stream().anyMatch(x -> x.isNumber() != result.getFirst().isNumber())) {
            throw new IllegalArgumentException("Mixed selector set value types: " + source);
        }
        return List.copyOf(result);
    }

    private static Condition parse(String body) {
        var missing = MISSING.matcher(body);
        if (missing.matches()) return new Missing(missing.group(1));
        var range = RANGE.matcher(body);
        if (range.matches()) {
            Literal lower = literal(range.group(1));
            Literal upper = literal(range.group(5));
            if (lower.number().compareTo(upper.number()) > 0) {
                throw new IllegalArgumentException("Reversed reward selector range: [" + body + "]");
            }
            return new Range(range.group(3), lower, range.group(2).equals("<="),
                    upper, range.group(4).equals("<="));
        }
        var member = MEMBER.matcher(body);
        if (member.matches()) return new Member(member.group(1), literals(member.group(2)));
        var compare = COMPARE.matcher(body);
        if (!compare.matches()) return null;
        String operator = compare.group(2);
        Literal value = literal(compare.group(3));
        if (!operator.equals("=") && !operator.equals("!=") && !value.isNumber()) {
            throw new IllegalArgumentException("Numeric selector comparison requires a number: [" + body + "]");
        }
        // Let css4j evaluate ordinary CSS equality, including its specificity.
        if (operator.equals("=") && !value.isNumber()) return null;
        return new Comparison(compare.group(1), operator, value);
    }

    private static int protectedEnd(String source, int offset) {
        if (source.startsWith("/*", offset)) {
            int end = source.indexOf("*/", offset + 2);
            return end < 0 ? source.length() : end + 2;
        }
        char quote = source.charAt(offset);
        if (quote != '\'' && quote != '"') return offset;
        for (int i = offset + 1; i < source.length(); i++) {
            if (source.charAt(i) == '\\') i++;
            else if (source.charAt(i) == quote) return i + 1;
        }
        return source.length();
    }

    private static int closingBracket(String source, int start) {
        for (int i = start + 1; i < source.length(); i++) {
            int end = protectedEnd(source, i);
            if (end > i) { i = end - 1; continue; }
            if (source.charAt(i) == ']') return i;
        }
        return -1;
    }

    static Rewrite rewrite(String source) {
        if (source.contains(PREFIX)) {
            throw new IllegalArgumentException("Reserved reward selector attribute prefix: " + PREFIX);
        }
        var replacements = new LinkedHashMap<String, Condition>();
        var output = new StringBuilder();
        for (int i = 0; i < source.length();) {
            int end = protectedEnd(source, i);
            if (end > i) { output.append(source, i, end); i = end; continue; }
            if (source.charAt(i) == '[') {
                int closing = closingBracket(source, i);
                if (closing != -1) {
                    String body = source.substring(i + 1, closing).trim();
                    Condition parsed = parse(body);
                    if (parsed != null) {
                        String attribute = PREFIX + replacements.size();
                        replacements.put(attribute, parsed);
                        output.append('[').append(attribute).append(']');
                        i = closing + 1;
                        continue;
                    }
                }
            }
            output.append(source.charAt(i++));
        }
        return new Rewrite(output.toString(), Map.copyOf(replacements));
    }
}
