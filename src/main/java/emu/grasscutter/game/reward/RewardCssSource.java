package emu.grasscutter.game.reward;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Translates only declaration names; selectors and values remain css4j's responsibility. */
final class RewardCssSource {
    private final String original;
    private final String css;
    private final Map<String, String> names;
    private final int[] offsets;

    private RewardCssSource(String original, String css, Map<String, String> names, int[] offsets) {
        this.original = original;
        this.css = css;
        this.names = Map.copyOf(names);
        this.offsets = offsets;
    }

    String css() { return css; }
    Map<String, String> names() { return names; }

    static RewardCssSource translate(String source) {
        var output = new StringBuilder();
        var offsets = new ArrayList<Integer>();
        var names = new LinkedHashMap<String, String>();
        int depth = 0, brackets = 0, parentheses = 0;
        boolean start = true;
        for (int i = 0; i < source.length();) {
            char c = source.charAt(i);
            int protectedEnd = protectedEnd(source, i);
            if (protectedEnd > i) {
                append(output, offsets, source.substring(i, protectedEnd), i, protectedEnd);
                if (c != '/') start = false;
                i = protectedEnd;
                continue;
            }
            if (start && depth > 0 && brackets == 0 && parentheses == 0 && !Character.isWhitespace(c)) {
                int end = i, angles = 0;
                while (end < source.length()) {
                    char candidate = source.charAt(end);
                    if ((Character.isWhitespace(candidate) && angles == 0)
                            || ":;{}()\"'/".indexOf(candidate) >= 0) break;
                    if (candidate == '<') angles++;
                    else if (candidate == '>') angles--;
                    end++;
                }
                String name = source.substring(i, end);
                int colon = skipTrivia(source, end);
                if (colon < source.length() && source.charAt(colon) == ':'
                        && nextDelimiter(source, colon + 1) != '{' && name.startsWith("--")) {
                    if (!name.matches("--[a-zA-Z_][a-zA-Z0-9_-]*")) {
                        throw new IllegalArgumentException("Invalid reward binding name: " + name);
                    }
                    register(names, name, name);
                }
                if (isDeclarationName(name) && colon < source.length() && source.charAt(colon) == ':'
                        && nextDelimiter(source, colon + 1) != '{') {
                    String internal = "--" + name.replace('.', '-');
                    register(names, internal, name);
                    // Template punctuation needs escaping only inside the library's input.
                    append(output, offsets, "--", i, i + 1);
                    for (int k = 0; k < name.length(); k++) {
                        char ch = name.charAt(k) == '.' ? '-' : name.charAt(k);
                        if (Character.isISOControl(ch)) {
                            append(output, offsets, "\\" + Integer.toHexString(ch) + " ", i + k, i + k + 1);
                        } else {
                            if (!(Character.isLetterOrDigit(ch) || ch == '-' || ch == '_' || ch >= 128)) {
                                append(output, offsets, "\\", i + k, i + k + 1);
                            }
                            append(output, offsets, String.valueOf(ch), i + k, i + k + 1);
                        }
                    }
                    i = end;
                    start = false;
                    continue;
                }
            }
            append(output, offsets, String.valueOf(c), i, i + 1);
            if (c == '\\' && i + 1 < source.length()) {
                i++;
                append(output, offsets, String.valueOf(source.charAt(i)), i, i + 1);
                start = false;
            } else if (c == '[') brackets++;
            else if (c == ']') brackets--;
            else if (c == '(') parentheses++;
            else if (c == ')') parentheses--;
            else if (brackets == 0 && parentheses == 0) {
                if (c == '{') { depth++; start = true; }
                else if (c == '}') { depth--; start = true; }
                else if (c == ';') start = true;
                else if (!Character.isWhitespace(c)) start = false;
            }
            i++;
        }
        offsets.add(source.length());
        return new RewardCssSource(source, output.toString(), names,
                offsets.stream().mapToInt(Integer::intValue).toArray());
    }

    private static boolean isDeclarationName(String name) {
        return name.startsWith("genshin.") || name.matches("[a-z][a-z0-9_]*\\.loot\\..+");
    }

    private static void register(Map<String, String> names, String internal, String name) {
        String previous = names.putIfAbsent(internal, name);
        if (previous != null && !previous.equals(name)) {
            throw new IllegalArgumentException("Reward CSS property collision: "
                    + previous + " and " + name + " map to " + internal);
        }
    }

    private static void append(StringBuilder output, ArrayList<Integer> offsets,
                               String text, int begin, int end) {
        output.append(text);
        for (int i = 0; i < text.length(); i++) offsets.add(Math.min(begin + i, end - 1));
    }

    private static int skipTrivia(String source, int offset) {
        while (offset < source.length()) {
            if (Character.isWhitespace(source.charAt(offset))) offset++;
            else if (source.startsWith("/*", offset)) offset = protectedEnd(source, offset);
            else break;
        }
        return offset;
    }

    /** Returns the end of a comment or quoted string, or the supplied offset otherwise. */
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

    /** A colon in a nested selector must not be mistaken for a declaration. */
    private static char nextDelimiter(String source, int offset) {
        int brackets = 0, parentheses = 0;
        for (int i = offset; i < source.length(); i++) {
            int end = protectedEnd(source, i);
            if (end > i) { i = end - 1; continue; }
            char c = source.charAt(i);
            if (c == '\\') i++;
            else if (c == '[') brackets++;
            else if (c == ']') brackets--;
            else if (c == '(') parentheses++;
            else if (c == ')') parentheses--;
            else if (brackets == 0 && parentheses == 0 && (c == ';' || c == '{' || c == '}')) return c;
        }
        return '\0';
    }

    String location(int line, int column) {
        int offset = 0;
        for (int current = 1; current < line && offset < css.length(); current++) {
            int newline = css.indexOf('\n', offset);
            offset = newline < 0 ? css.length() : newline + 1;
        }
        offset = offsets[Math.min(css.length(), offset + Math.max(0, column - 1))];
        int originalLine = 1, originalColumn = 1;
        for (int i = 0; i < offset; i++) {
            if (original.charAt(i) == '\n') { originalLine++; originalColumn = 1; }
            else originalColumn++;
        }
        return originalLine + ":" + originalColumn;
    }

    String display(String message) {
        if (message == null) return "";
        // Longer names first, so a short resource cannot replace another resource's prefix.
        for (var internal : names.keySet().stream().sorted((a, b) -> Integer.compare(b.length(), a.length())).toList()) {
            message = message.replace(internal, names.get(internal));
        }
        return message;
    }
}
