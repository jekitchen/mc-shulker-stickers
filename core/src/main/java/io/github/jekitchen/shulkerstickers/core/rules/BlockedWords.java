package io.github.jekitchen.shulkerstickers.core.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The {@code name.blocked_words} list, checked at Save. An entry wrapped in slashes
 * ({@code /b[a4]d/}) is a regular expression; anything else matches as a whole word.
 * Matching ignores case. Invalid regular expressions are skipped with a warning.
 */
public final class BlockedWords {

    private record Entry(String source, Pattern pattern) {}

    private final List<Entry> entries;
    private final List<String> warnings;

    private BlockedWords(List<Entry> entries, List<String> warnings) {
        this.entries = List.copyOf(entries);
        this.warnings = List.copyOf(warnings);
    }

    public static BlockedWords compile(List<String> sources) {
        List<Entry> entries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (String source : sources) {
            if (source.isBlank()) {
                continue;
            }
            boolean regex = source.length() >= 2 && source.startsWith("/") && source.endsWith("/");
            String expression = regex
                    ? source.substring(1, source.length() - 1)
                    : "\\b" + Pattern.quote(source.strip()) + "\\b";
            try {
                entries.add(new Entry(source, Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)));
            } catch (PatternSyntaxException e) {
                warnings.add("Blocked word " + source + " is not a valid regular expression and was skipped");
            }
        }
        return new BlockedWords(entries, warnings);
    }

    /** How long one name may be checked against one entry before it is refused anyway. */
    static final long MATCH_BUDGET_NANOS = 50_000_000L;

    /**
     * The first entry (as written in the config) that matches {@code name}. A regular expression
     * written so that some inputs take very long (catastrophic backtracking, e.g. {@code (a|aa)+$})
     * would let a player stall the server with a chosen name; a check that runs out of its time
     * budget counts as a match, so the name is refused and nothing stalls.
     */
    public Optional<String> firstMatch(String name) {
        for (Entry entry : entries) {
            try {
                if (entry.pattern().matcher(new Budgeted(name, System.nanoTime() + MATCH_BUDGET_NANOS)).find()) {
                    return Optional.of(entry.source());
                }
            } catch (OutOfTime e) {
                return Optional.of(entry.source());
            }
        }
        return Optional.empty();
    }

    private static final class OutOfTime extends RuntimeException {
        OutOfTime() {
            super(null, null, false, false);
        }
    }

    /** The name, read by the matcher; reading it past the deadline stops the match. */
    private record Budgeted(CharSequence text, long deadline) implements CharSequence {
        @Override
        public char charAt(int index) {
            if (System.nanoTime() > deadline) {
                throw new OutOfTime();
            }
            return text.charAt(index);
        }

        @Override
        public int length() {
            return text.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new Budgeted(text.subSequence(start, end), deadline);
        }

        @Override
        public String toString() {
            return text.toString();
        }
    }

    public List<String> warnings() {
        return warnings;
    }
}
