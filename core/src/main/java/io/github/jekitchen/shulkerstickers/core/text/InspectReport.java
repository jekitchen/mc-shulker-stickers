package io.github.jekitchen.shulkerstickers.core.text;

import io.github.jekitchen.shulkerstickers.core.data.StickerCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What {@code /shulkerstickers inspect} prints, shared so both platforms print the same lines in
 * the same order (owner decision 11):
 *
 * <ol>
 *   <li>the header with the box position;
 *   <li>"no sticker data", when there is neither sticker data nor a name;
 *   <li>one line per stored key, as found (readable or not);
 *   <li>a warning when the stored data can't be read by this version;
 *   <li>the name, marked "(hidden)" when it isn't drawn.
 * </ol>
 */
public final class InspectReport {

    public enum Tone { HEADER, NORMAL, WARNING }

    /** One line: a translation key, its arguments, and how to color it. */
    public record Line(Tone tone, String key, List<Object> args) {
        public Line {
            args = List.copyOf(args);
        }
    }

    private InspectReport() {}

    /**
     * @param raw our keys (short names) as found on the box, without {@code name_hidden}
     * @param name the box's custom name as plain text, if any
     */
    public static List<Line> lines(String position, Map<String, Object> raw, Optional<String> name, boolean nameHidden) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(Tone.HEADER, TextKeys.INSPECT_HEADER, List.of(position)));
        if (raw.isEmpty() && name.isEmpty()) {
            lines.add(new Line(Tone.NORMAL, TextKeys.INSPECT_NONE, List.of()));
            return lines;
        }
        raw.forEach((key, value) -> lines.add(new Line(Tone.NORMAL, TextKeys.INSPECT_LINE, List.of(key, value))));
        if (!raw.isEmpty() && StickerCodec.decode(raw).isEmpty()) {
            lines.add(new Line(Tone.WARNING, TextKeys.INSPECT_UNREADABLE, List.of()));
        }
        name.ifPresent(n -> lines.add(new Line(Tone.NORMAL, TextKeys.INSPECT_LINE,
                List.of("name", n + (nameHidden ? " (hidden)" : "")))));
        return lines;
    }
}
