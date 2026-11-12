package io.github.jekitchen.shulkerstickers.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Security: a badly written blocked-word regex can't be used to stall the server with a chosen name. */
class BlockedWordsTimeTest {

    @Test
    void aCatastrophicRegexIsCutOffAndTheNameRefused() {
        // Java 25 defuses the classic (a+)+b by memoizing, but not once a backreference is involved:
        // without a budget this one ran for over a minute on a 27-character name.
        String regex = "/((a+)\\2?)+b/";
        BlockedWords words = BlockedWords.compile(List.of(regex));
        String name = "a".repeat(26) + "c";
        Optional<String> match = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> words.firstMatch(name));
        assertEquals(Optional.of(regex), match, "out of time counts as a match");
    }

    @Test
    void ordinaryEntriesStillWork() {
        BlockedWords words = BlockedWords.compile(List.of("grief", "/b[a4]d/"));
        assertEquals(Optional.of("grief"), words.firstMatch("no grief here"));
        assertEquals(Optional.of("/b[a4]d/"), words.firstMatch("so b4d"));
        assertEquals(Optional.empty(), words.firstMatch("griefing is a different word"));
        assertEquals(Optional.empty(), words.firstMatch("Ores"));
    }
}
