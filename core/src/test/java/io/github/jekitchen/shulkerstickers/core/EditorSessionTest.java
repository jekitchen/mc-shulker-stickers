package io.github.jekitchen.shulkerstickers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class EditorSessionTest {

    private static final BoxState ORIGINAL =
            new BoxState(BoxColor.UNDYED, Optional.of("minecraft:stone"), Optional.of("Ores"));

    @Test
    void freshSessionHasNoChanges() {
        EditorSession session = new EditorSession(ORIGINAL);

        assertFalse(session.isDirty());
        assertEquals(ORIGINAL, session.staged());
        assertTrue(session.changes().isEmpty());
    }

    @Test
    void stagingAColorDoesNotTouchOtherFields() {
        EditorSession session = new EditorSession(ORIGINAL);

        session.stageColor(BoxColor.RED);

        assertTrue(session.isDirty());
        assertEquals(new BoxState(BoxColor.RED, ORIGINAL.iconId(), ORIGINAL.name()), session.staged());
        assertEquals(new Changes(true, false, false), session.changes());
    }

    @Test
    void stagingTheOriginalValueAgainIsNotAChange() {
        EditorSession session = new EditorSession(ORIGINAL);

        session.stageColor(BoxColor.RED);
        session.stageColor(BoxColor.UNDYED);

        assertFalse(session.isDirty());
    }

    @Test
    void removingTheIconStagesAnEmptyIcon() {
        EditorSession session = new EditorSession(ORIGINAL);

        session.removeIcon();

        assertEquals(Optional.empty(), session.staged().iconId());
        assertEquals(new Changes(false, true, false), session.changes());
    }

    @Test
    void stagingAnIconReplacesTheOldOne() {
        EditorSession session = new EditorSession(ORIGINAL);

        session.stageIcon("minecraft:cobblestone");

        assertEquals(Optional.of("minecraft:cobblestone"), session.staged().iconId());
    }

    @Test
    void sameItemWithDifferentVisualsIsAnIconChange() {
        BoxState dyed = new BoxState(BoxColor.UNDYED, Optional.of("minecraft:leather_helmet"),
                Optional.of("{\"minecraft:dyed_color\":255}"), Optional.empty());
        EditorSession session = new EditorSession(dyed);

        session.stageIcon("minecraft:leather_helmet", Optional.of("{\"minecraft:dyed_color\":16711680}"));

        assertEquals(new Changes(false, true, false), session.changes());
        assertEquals(Optional.of("{\"minecraft:dyed_color\":16711680}"), session.staged().iconVisual());
    }

    @Test
    void removingTheIconAlsoClearsItsVisuals() {
        BoxState dyed = new BoxState(BoxColor.UNDYED, Optional.of("minecraft:leather_helmet"),
                Optional.of("{}"), Optional.empty());
        EditorSession session = new EditorSession(dyed);

        session.removeIcon();

        assertEquals(Optional.empty(), session.staged().iconVisual());
    }

    @Test
    void threeArgumentStateHasNoVisuals() {
        assertEquals(Optional.empty(), ORIGINAL.iconVisual());
    }

    @Test
    void blankNameStagesNameRemoval() {
        EditorSession session = new EditorSession(ORIGINAL);

        session.stageName("   ");

        assertEquals(Optional.empty(), session.staged().name());
        assertEquals(new Changes(false, false, true), session.changes());
    }

    @Test
    void nameIsTrimmed() {
        EditorSession session = new EditorSession(ORIGINAL);

        session.stageName("  Redstone  ");

        assertEquals(Optional.of("Redstone"), session.staged().name());
    }
}
