package emu.grasscutter.game.reward;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Parser for the reward stylesheet language. This is deliberately a small
 * independent grammar, not a CSS browser compatibility implementation.
 */
final class RewardCssParser {
    record Declaration(String name, String value) {}
    record Rule(List<Selector> selectors, List<Declaration> declarations) {}

    record Selector(String type, List<Condition> predicates) {
        Selector {
            predicates = List.copyOf(predicates);
        }

        boolean matches(String objectType, Map<String, String> attributes) {
            if (!type.equals(objectType)) return false;
            for (Condition predicate : predicates) {
                if (!predicate.matches(attributes)) return false;
            }
            return true;
        }

        // Only type and attribute selectors exist in this grammar.
        int specificity() { return predicates.size(); }
    }

    sealed interface Condition permits Exists, Missing, Compare, Between, In {
        boolean matches(Map<String, String> attributes);
    }

    record Exists(String name) implements Condition {
        public boolean matches(Map<String, String> values) {
            return values.containsKey(name);
        }
    }

    record Missing(String name) implements Condition {
        public boolean matches(Map<String, String> values) {
            return !values.containsKey(name);
        }
    }

    record Literal(String string, BigDecimal numeric) {
        static Literal of(String text, boolean quoted) {
            if (!quoted && NUMBER.matcher(text).matches()) {
                return new Literal(null, new BigDecimal(text));
            }
            return new Literal(text, null);
        }

        boolean matches(String actual) {
            if (actual == null) return false;
            if (numeric == null) return string.equals(actual);
            try { return numeric.compareTo(new BigDecimal(actual)) == 0; }
            catch (NumberFormatException ex) { return false; }
        }

        Integer compare(String actual) {
            if (numeric == null || actual == null) return null;
            try { return new BigDecimal(actual).compareTo(numeric); }
            catch (NumberFormatException ex) { return null; }
        }

        boolean numeric() { return numeric != null; }
    }

    record Compare(String name, String operator, Literal value) implements Condition {
        public boolean matches(Map<String, String> attributes) {
            String actual = attributes.get(name);
            if (actual == null) return false;
            if (operator.equals("=")) return value.matches(actual);
            if (operator.equals("!=")) {
                // Numeric comparisons require a numeric actual value.
                return !value.matches(actual) && (!value.numeric() || value.compare(actual) != null);
            }
            Integer comparison = value.compare(actual);
            if (comparison == null) return false;
            return switch (operator) {
                case "<" -> comparison < 0;
                case "<=" -> comparison <= 0;
                case ">" -> comparison > 0;
                case ">=" -> comparison >= 0;
                default -> throw new IllegalStateException("Unsupported operator: " + operator);
            };
        }
    }

    record Between(String name, Literal low, boolean lowInclusive,
                   Literal high, boolean highInclusive) implements Condition {
        public boolean matches(Map<String, String> attributes) {
            String actual = attributes.get(name);
            Integer lower = low.compare(actual), upper = high.compare(actual);
            return lower != null && upper != null
                    && (lower > 0 || lower == 0 && lowInclusive)
                    && (upper < 0 || upper == 0 && highInclusive);
        }
    }

    record In(String name, List<Literal> values) implements Condition {
        In { values = List.copyOf(values); }
        public boolean matches(Map<String, String> attributes) {
            String actual = attributes.get(name);
            if (actual == null) return false;
            for (Literal value : values) if (value.matches(actual)) return true;
            return false;
        }
    }

    private static final Pattern NUMBER = Pattern.compile("[+-]?[0-9]+(?:\\.[0-9]+)?");
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");
    private static final Pattern PROPERTY = Pattern.compile(
            "(?:--[A-Za-z_][A-Za-z0-9_-]*|genshin\\.[A-Za-z0-9_.$\\[\\]-]+(?:\\.[A-Za-z0-9_.$\\[\\]-]+)*|[a-z][a-z0-9_]*\\.loot\\..+)");
    private final String source;
    private int position;

    private RewardCssParser(String source) { this.source = Objects.requireNonNull(source); }

    static List<Rule> parse(String source) {
        var parser = new RewardCssParser(source);
        var result = new ArrayList<Rule>();
        parser.trivia();
        while (!parser.end()) {
            List<Selector> selectors = parser.selectors();
            parser.trivia();
            parser.expect('{');
            var declarations = new ArrayList<Declaration>();
            parser.trivia();
            while (!parser.take('}')) {
                if (parser.end()) throw parser.error("Missing '}'");
                declarations.add(parser.declaration());
                parser.trivia();
            }
            result.add(new Rule(selectors, List.copyOf(declarations)));
            parser.trivia();
        }
        return List.copyOf(result);
    }

    private List<Selector> selectors() {
        var result = new ArrayList<Selector>();
        do {
            trivia();
            String type = identifier();
            var predicates = new ArrayList<Condition>();
            trivia();
            while (take('[')) {
                trivia();
                predicates.add(condition());
                trivia();
                expect(']');
                trivia();
            }
            result.add(new Selector(type, predicates));
            if (!take(',')) break;
        } while (true);
        return List.copyOf(result);
    }

    private Condition condition() {
        if (take('!')) {
            trivia();
            return new Missing(identifier());
        }
        String left = atom();
        boolean leftNumber = NUMBER.matcher(left).matches();
        trivia();
        if (peek(']')) {
            if (leftNumber) throw error("Expected an attribute name");
            return new Exists(left);
        }
        if (word("in")) {
            if (leftNumber) throw error("Set membership requires an attribute name");
            trivia();
            expect('(');
            var values = new ArrayList<Literal>();
            do {
                trivia();
                values.add(literal());
                trivia();
                if (!take(',')) break;
            } while (true);
            expect(')');
            if (values.isEmpty()) throw error("Membership list cannot be empty");
            boolean numeric = values.getFirst().numeric();
            if (values.stream().anyMatch(v -> v.numeric() != numeric)) {
                throw error("Membership list has mixed value types");
            }
            return new In(left, values);
        }
        String operator = operator();
        trivia();
        if (leftNumber) {
            if (!operator.equals("<") && !operator.equals("<=")) {
                throw error("Range lower bound must use '<' or '<='");
            }
            String name = identifier();
            trivia();
            String next = operator();
            if (!next.equals("<") && !next.equals("<=")) {
                throw error("Range upper bound must use '<' or '<='");
            }
            trivia();
            Literal high = literal();
            if (!high.numeric()) throw error("Range upper bound must be numeric");
            Literal low = Literal.of(left, false);
            if (low.numeric.compareTo(high.numeric) > 0) throw error("Reversed numeric range");
            return new Between(name, low, operator.equals("<="), high, next.equals("<="));
        }
        Literal value = literal();
        if (!operator.equals("=") && !operator.equals("!=") && !value.numeric()) {
            throw error("Numeric comparison requires a number");
        }
        return new Compare(left, operator, value);
    }

    private Declaration declaration() {
        trivia();
        int start = position, round = 0, square = 0, angle = 0;
        while (!end()) {
            char c = current();
            if (c == ':' && round == 0 && square == 0 && angle == 0) break;
            if (c == '{' || c == '}' || c == ';') throw error("Expected ':' after property");
            if (source.startsWith("/*", position)) { trivia(); continue; }
            if (c == '(') round++;
            else if (c == ')') round--;
            else if (c == '[') square++;
            else if (c == ']') square--;
            else if (c == '<') angle++;
            else if (c == '>') angle--;
            if (round < 0 || square < 0 || angle < 0) throw error("Invalid resource property");
            position++;
        }
        if (end() || round != 0 || square != 0 || angle != 0) throw error("Invalid resource declaration");
        String property = source.substring(start, position).replaceAll("/\\*.*?\\*/", "").trim();
        if (!PROPERTY.matcher(property).matches() || property.startsWith("--grant-")) {
            throw error("Invalid reward property: " + property);
        }
        expect(':');
        trivia();
        var value = new StringBuilder();
        char quote = 0;
        while (!end()) {
            char c = current();
            if (quote != 0) {
                value.append(c);
                position++;
                if (c == '\\') {
                    if (end()) throw error("Unterminated escape sequence");
                    value.append(current());
                    position++;
                } else if (c == quote) quote = 0;
                continue;
            }
            if (source.startsWith("/*", position)) { trivia(); continue; }
            if (c == '"' || c == '\'') { quote = c; value.append(c); position++; continue; }
            if (c == ';') break;
            if (c == '{' || c == '}') throw error("Missing ';' after declaration");
            value.append(c);
            position++;
        }
        if (quote != 0) throw error("Unterminated declaration string");
        expect(';');
        String text = value.toString().trim();
        if (text.isEmpty()) throw error("Empty reward value");
        if (text.matches("(?s).*\\s+!important\\s*") || text.endsWith("!important")) {
            throw error("!important is not supported");
        }
        if (text.length() >= 2
                && ((text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("'") && text.endsWith("'")))) {
            text = text.substring(1, text.length() - 1);
        }
        return new Declaration(property, text);
    }

    private Literal literal() {
        trivia();
        if (take('"')) return new Literal(quoted('"'), null);
        if (take('\'')) return new Literal(quoted('\''), null);
        String word = atom();
        if (!NUMBER.matcher(word).matches() && !NAME.matcher(word).matches()) {
            throw error("Invalid literal: " + word);
        }
        return Literal.of(word, false);
    }

    private String quoted(char quote) {
        var value = new StringBuilder();
        while (!end()) {
            char c = source.charAt(position++);
            if (c == quote) return value.toString();
            if (c == '\\') {
                if (end()) throw error("Unterminated escape");
                value.append(source.charAt(position++));
            } else value.append(c);
        }
        throw error("Unterminated string literal");
    }

    private String atom() {
        trivia();
        int start = position;
        while (!end()) {
            char c = current();
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '+' || c == '.') position++;
            else break;
        }
        if (start == position) throw error("Expected literal or attribute name");
        return source.substring(start, position);
    }

    private String identifier() {
        String word = atom();
        if (!NAME.matcher(word).matches()) throw error("Invalid identifier: " + word);
        return word;
    }

    private String operator() {
        if (takeString("!=")) return "!=";
        if (takeString(">=")) return ">=";
        if (takeString("<=")) return "<=";
        for (char c : new char[] {'=', '<', '>'}) if (take(c)) return Character.toString(c);
        throw error("Expected comparison operator");
    }

    private boolean word(String candidate) {
        if (!source.startsWith(candidate, position)) return false;
        int after = position + candidate.length();
        if (after < source.length() && (Character.isLetterOrDigit(source.charAt(after))
                || source.charAt(after) == '_')) return false;
        position = after;
        return true;
    }

    private void trivia() {
        for (;;) {
            while (!end() && Character.isWhitespace(current())) position++;
            if (!source.startsWith("/*", position)) return;
            int close = source.indexOf("*/", position + 2);
            if (close < 0) throw error("Unterminated comment");
            position = close + 2;
        }
    }

    private boolean takeString(String expected) {
        if (!source.startsWith(expected, position)) return false;
        position += expected.length();
        return true;
    }

    private boolean take(char c) {
        if (!peek(c)) return false;
        position++;
        return true;
    }

    private boolean peek(char c) { return !end() && current() == c; }
    private char current() { return source.charAt(position); }
    private boolean end() { return position >= source.length(); }
    private void expect(char c) { if (!take(c)) throw error("Expected '" + c + "'"); }

    private IllegalArgumentException error(String message) {
        int line = 1, column = 1;
        for (int i = 0; i < position; i++) {
            if (source.charAt(i) == '\n') { line++; column = 1; }
            else column++;
        }
        return new IllegalArgumentException("Invalid reward CSS: " + line + ":" + column + " " + message);
    }
}
