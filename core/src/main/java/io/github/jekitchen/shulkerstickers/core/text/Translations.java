package io.github.jekitchen.shulkerstickers.core.text;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Server-side translation (PRD §5.2): vanilla clients don't have our language files, so
 * every string is translated before it's sent. Falls back to English, then to the key.
 */
public final class Translations {

    public static final String FALLBACK = "en_us";
    /** Languages bundled in {@code shulker_stickers/lang/}. */
    public static final List<String> BUNDLED = List.of("en_us");

    private final Map<String, Map<String, String>> languages;

    public Translations(Map<String, Map<String, String>> languages) {
        this.languages = Map.copyOf(languages);
    }

    public static Translations loadBundled() {
        Map<String, Map<String, String>> languages = new HashMap<>();
        for (String locale : BUNDLED) {
            String path = "/shulker_stickers/lang/" + locale + ".json";
            try (InputStream in = Translations.class.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("Missing bundled language file " + path);
                }
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                Map<String, String> entries = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                    entries.put(e.getKey(), e.getValue().getAsString());
                }
                languages.put(locale, entries);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return new Translations(languages);
    }

    public boolean has(String locale, String key) {
        Map<String, String> language = languages.get(normalize(locale));
        return language != null && language.containsKey(key);
    }

    /** Translates {@code key}, replacing {@code {0}}, {@code {1}}, … with {@code args}. */
    public String translate(String locale, String key, Object... args) {
        String template = lookup(normalize(locale), key);
        if (template == null) {
            template = lookup(FALLBACK, key);
        }
        if (template == null) {
            return key;
        }
        String result = template;
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return result;
    }

    private String lookup(String locale, String key) {
        Map<String, String> language = languages.get(locale);
        return language == null ? null : language.get(key);
    }

    /** {@code en_US} and {@code en-us} both become {@code en_us}, as Minecraft clients report. */
    public static String normalize(String locale) {
        return locale == null ? FALLBACK : locale.toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
