package io.github.jekitchen.shulkerstickers.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TranslationsTest {

    private final Translations bundled = Translations.loadBundled();

    @Test
    void translatesEnglish() {
        assertEquals("Save", bundled.translate("en_us", TextKeys.EDITOR_SAVE));
    }

    @Test
    void fillsPlaceholders() {
        assertEquals("Being edited by Alex", bundled.translate("en_us", TextKeys.DENY_IN_USE_EDITOR, "Alex"));
    }

    @Test
    void normalizesLocale() {
        assertEquals("Save", bundled.translate("en_US", TextKeys.EDITOR_SAVE));
        assertEquals("Save", bundled.translate("EN-us", TextKeys.EDITOR_SAVE));
    }

    @Test
    void unknownLocaleFallsBackToEnglish() {
        assertEquals("Save", bundled.translate("xx_yy", TextKeys.EDITOR_SAVE));
    }

    @Test
    void missingKeyInLanguageFallsBackToEnglish() {
        Translations t = new Translations(Map.of(
                "en_us", Map.of("a", "Apple", "b", "Banana"),
                "de_de", Map.of("a", "Apfel")));

        assertEquals("Apfel", t.translate("de_de", "a"));
        assertEquals("Banana", t.translate("de_de", "b"));
    }

    @Test
    void keyMissingEverywhereShowsTheKey() {
        assertEquals("no.such.key", bundled.translate("en_us", "no.such.key"));
    }

    @Test
    void extraArgumentsAndMissingArgumentsAreHarmless() {
        Translations t = new Translations(Map.of("en_us", Map.of("k", "{0} and {1}")));

        assertEquals("x and {1}", t.translate("en_us", "k", "x"));
        assertEquals("x and y", t.translate("en_us", "k", "x", "y", "z"));
    }

    @Test
    void everyTextKeyConstantHasEnglishText() throws IllegalAccessException {
        for (Field field : TextKeys.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                String key = (String) field.get(null);
                assertTrue(bundled.has("en_us", key), "missing en_us text for " + key);
            }
        }
    }

    @Test
    void everyColorHasEnglishText() {
        for (BoxColor color : BoxColor.values()) {
            assertTrue(bundled.has("en_us", TextKeys.color(color)), color.name());
        }
    }
}
