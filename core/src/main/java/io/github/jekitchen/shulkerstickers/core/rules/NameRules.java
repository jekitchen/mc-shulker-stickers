package io.github.jekitchen.shulkerstickers.core.rules;

/** Name length and how a name is shown on the box (PRD §5.4). */
public final class NameRules {

    /** The vanilla anvil rename field's limit ({@code AnvilMenu.MAX_NAME_LENGTH}). */
    public static final int MAX_LENGTH = 50;

    private NameRules() {}

    /** The text drawn on the box: at most {@code maxChars} code points, then "…". */
    public static String displayText(String name, int maxChars) {
        if (name.codePointCount(0, name.length()) <= maxChars) {
            return name;
        }
        return name.substring(0, name.offsetByCodePoints(0, maxChars)) + "…";
    }

    /** Cuts input to {@link #MAX_LENGTH} code points. */
    public static String clamp(String name) {
        if (name.codePointCount(0, name.length()) <= MAX_LENGTH) {
            return name;
        }
        return name.substring(0, name.offsetByCodePoints(0, MAX_LENGTH));
    }
}
