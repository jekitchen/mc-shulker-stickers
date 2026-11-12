package io.github.jekitchen.shulkerstickers.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.jekitchen.shulkerstickers.core.text.InspectReport.Line;
import io.github.jekitchen.shulkerstickers.core.text.InspectReport.Tone;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InspectReportTest {

    private static Map<String, Object> raw(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void emptyBoxSaysSo() {
        assertEquals(List.of(
                new Line(Tone.HEADER, TextKeys.INSPECT_HEADER, List.of("1 2 3")),
                new Line(Tone.NORMAL, TextKeys.INSPECT_NONE, List.of())),
                InspectReport.lines("1 2 3", Map.of(), Optional.empty(), false));
    }

    @Test
    void stickerAndHiddenNameInOrder() {
        List<Line> lines = InspectReport.lines("0 0 0",
                raw("v", 1, "icon_id", "minecraft:stone", "anchor", "lid", "rot", 0), Optional.of("Ores"), true);
        assertEquals(6, lines.size());
        assertEquals(new Line(Tone.NORMAL, TextKeys.INSPECT_LINE, List.of("v", 1)), lines.get(1));
        assertEquals(new Line(Tone.NORMAL, TextKeys.INSPECT_LINE, List.of("name", "Ores (hidden)")), lines.get(5));
    }

    @Test
    void unreadableDataIsFlaggedAfterTheRawLines() {
        List<Line> lines = InspectReport.lines("0 0 0", raw("v", 99, "icon_id", "x:y"), Optional.empty(), false);
        assertEquals(Tone.WARNING, lines.getLast().tone());
        assertEquals(TextKeys.INSPECT_UNREADABLE, lines.getLast().key());
    }

    @Test
    void nameOnlyBoxShowsJustTheName() {
        List<Line> lines = InspectReport.lines("0 0 0", Map.of(), Optional.of("Wood"), false);
        assertEquals(List.of(
                new Line(Tone.HEADER, TextKeys.INSPECT_HEADER, List.of("0 0 0")),
                new Line(Tone.NORMAL, TextKeys.INSPECT_LINE, List.of("name", "Wood"))), lines);
    }
}
