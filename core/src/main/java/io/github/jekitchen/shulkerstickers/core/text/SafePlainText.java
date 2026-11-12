package io.github.jekitchen.shulkerstickers.core.text;

/**
 * Builds the plain text of an untrusted text component (a box's name can come from any crafted
 * item) within a fixed budget. Each platform walks its own component type and feeds the parts in.
 *
 * <p>Why not the platforms' own "to plain string": they expand translation arguments, so a name
 * nesting {@code translate} with a fallback like {@code "%1$s%1$s…"} a few levels deep grows to
 * billions of characters and hangs or crashes the server whenever the box is drawn. Walkers using
 * this class never expand arguments (a translatable part contributes its own text only), stop
 * after {@link #MAX_PARTS} parts, and keep at most {@link #MAX_CHARS} characters.
 */
public final class SafePlainText {

    /** Parts (components) visited at most; deeper or wider names are cut off. */
    public static final int MAX_PARTS = 256;
    /** Characters kept at most; names are shortened for display far below this anyway. */
    public static final int MAX_CHARS = 512;

    private final StringBuilder out = new StringBuilder();
    private int parts;

    /** Call before each part; false when the budget is spent and the walk should stop. */
    public boolean visit() {
        return ++parts <= MAX_PARTS && out.length() < MAX_CHARS;
    }

    public void append(String text) {
        if (text == null) {
            return;
        }
        int room = MAX_CHARS - out.length();
        if (room > 0) {
            out.append(text, 0, Math.min(room, text.length()));
        }
    }

    /** The text, without formatting codes ({@code §}) or control characters. */
    public String result() {
        return clean(out);
    }

    /** Drops {@code §} and control characters, as vanilla does for typed names. */
    public static String clean(CharSequence text) {
        StringBuilder cleaned = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '§' && c >= ' ' && c != 127) {
                cleaned.append(c);
            }
        }
        return cleaned.toString();
    }
}
