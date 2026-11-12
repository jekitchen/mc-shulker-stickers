package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.core.text.SafePlainText;
import java.time.Duration;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

/** A crafted box name must not hang or exhaust the server when it is turned into text. */
class SafeNamesTest {

    /** Nine levels of {@code translate} whose fallback repeats the argument ten times: 10^9 characters if expanded. */
    static Component nameBomb() {
        Component inner = Component.text("x");
        for (int level = 0; level < 9; level++) {
            inner = Component.translatable("nope." + level, "%1$s%1$s%1$s%1$s%1$s%1$s%1$s%1$s%1$s%1$s", inner);
        }
        return inner;
    }

    @Test
    void aNestedTranslationBombIsCheapAndShort() {
        String text = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> SafeNames.plain(nameBomb()));
        assertTrue(text.length() <= SafePlainText.MAX_CHARS, "length " + text.length());
    }

    @Test
    void aWideOrDeepNameIsCutOff() {
        Component wide = Component.empty();
        for (int i = 0; i < 10_000; i++) {
            wide = wide.append(Component.text("ab"));
        }
        Component deep = Component.text("z");
        for (int i = 0; i < 5_000; i++) {
            deep = Component.text("y").append(deep);
        }
        assertTrue(SafeNames.plain(wide).length() <= SafePlainText.MAX_CHARS);
        assertTrue(SafeNames.plain(deep).length() <= SafePlainText.MAX_PARTS);
    }

    @Test
    void ordinaryNamesAreUnchangedAndFormattingCodesAreDropped() {
        assertEquals("Ores", SafeNames.plain(Component.text("Ores")));
        assertEquals("Iron and gold", SafeNames.plain(Component.text("Iron ").append(Component.text("and gold"))));
        // Like vanilla's filter for typed names: the section sign goes (so no formatting), its letter stays.
        assertEquals("cred", SafeNames.plain(Component.text("§cred\u0007")));
    }
}
