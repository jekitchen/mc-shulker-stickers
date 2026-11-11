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

    /** The first entry (as written in the config) that matches {@code name}. */
    public Optional<String> firstMatch(String name) {
        return entries.stream().filter(e -> e.pattern().matcher(name).find()).map(Entry::source).findFirst();
    }

    public List<String> warnings() {
        return warnings;
    }
}
