package io.github.jekitchen.shulkerstickers.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NameRulesTest {

    @Test
    void maxLengthMatchesVanillaAnvil() {
        assertEquals(50, NameRules.MAX_LENGTH);
    }

    @Test
    void shortNamesAreShownWhole() {
        assertEquals("Redstone", NameRules.displayText("Redstone", 24));
    }

    @Test
    void longNamesAreTruncatedWithEllipsis() {
        assertEquals("abcdefghij…", NameRules.displayText("abcdefghijklmnop", 10));
    }

    @Test
    void nameExactlyAtTheLimitIsNotTruncated() {
        assertEquals("abcdefghij", NameRules.displayText("abcdefghij", 10));
    }

    @Test
    void truncatesOnCodePoints() {
        String emoji = "📦"; // one code point, two chars
        assertEquals(emoji.repeat(3) + "…", NameRules.displayText(emoji.repeat(5), 3));
    }

    @Test
    void clampToMaxLengthCutsLongInput() {
        assertEquals("x".repeat(50), NameRules.clamp("x".repeat(80)));
        assertEquals("short", NameRules.clamp("short"));
    }

    @Test
    void plainWordsMatchWholeWordsIgnoringCase() {
        BlockedWords words = BlockedWords.compile(List.of("bad"));

        assertEquals(Optional.of("bad"), words.firstMatch("My BAD box"));
        assertEquals(Optional.empty(), words.firstMatch("Badges"));
    }

    @Test
    void slashWrappedEntriesAreRegex() {
        BlockedWords words = BlockedWords.compile(List.of("/b[a4]d/"));

        assertEquals(Optional.of("/b[a4]d/"), words.firstMatch("so b4d"));
        assertEquals(Optional.of("/b[a4]d/"), words.firstMatch("BAD"));
    }

    @Test
    void invalidRegexIsSkipped() {
        BlockedWords words = BlockedWords.compile(List.of("/[unclosed/", "ok"));

        assertEquals(1, words.warnings().size());
        assertTrue(words.warnings().getFirst().contains("[unclosed"));
        assertEquals(Optional.of("ok"), words.firstMatch("ok then"));
    }

    @Test
    void blankEntriesAreIgnored() {
        BlockedWords words = BlockedWords.compile(List.of("", "  "));

        assertEquals(Optional.empty(), words.firstMatch("anything"));
        assertEquals(List.of(), words.warnings());
    }
}
