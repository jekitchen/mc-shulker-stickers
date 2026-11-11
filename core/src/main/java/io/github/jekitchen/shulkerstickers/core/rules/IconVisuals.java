package io.github.jekitchen.shulkerstickers.core.rules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The stored form of an icon's visual properties ({@code icon_visual}). It is compact JSON:
 * an object from component ID to the value Minecraft's own component codec produces with
 * JSON ops, holding only {@link IconRules#VISUAL_COMPONENTS}, with keys sorted at every level.
 * Both platforms produce it from the same codecs, so the string is identical on Fabric and Paper.
 */
public final class IconVisuals {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private IconVisuals() {}

    private static final String GLINT_OVERRIDE = "minecraft:enchantment_glint_override";

    /** Filters and sorts a component map; empty if nothing visual is left. */
    public static Optional<String> canonicalize(JsonObject components) {
        return canonicalize(components, false);
    }

    /**
     * Like {@link #canonicalize(JsonObject)}, but an {@code enchanted} item keeps its shine:
     * enchantments aren't stored, so a glint override of {@code true} is added unless the
     * item already sets one.
     */
    public static Optional<String> canonicalize(JsonObject components, boolean enchanted) {
        JsonObject filtered = filter(components);
        if (enchanted && !filtered.has(GLINT_OVERRIDE)) {
            filtered.addProperty(GLINT_OVERRIDE, true);
        }
        return filtered.isEmpty() ? Optional.empty() : Optional.of(GSON.toJson(sorted(filtered)));
    }

    /** Reads stored data back, re-applying the filter because stored data is untrusted. */
    public static Optional<JsonObject> parse(String stored) {
        try {
            JsonElement element = JsonParser.parseString(stored);
            return element.isJsonObject() ? Optional.of(filter(element.getAsJsonObject())) : Optional.empty();
        } catch (JsonParseException e) {
            return Optional.empty();
        }
    }

    private static JsonObject filter(JsonObject components) {
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> e : components.entrySet()) {
            if (e.getKey().startsWith("!")) {
                continue;
            }
            String id = IconRules.withNamespace(e.getKey());
            if (IconRules.isVisualComponent(id)) {
                out.add(id, e.getValue());
            }
        }
        return out;
    }

    private static JsonElement sorted(JsonElement element) {
        if (element.isJsonObject()) {
            TreeMap<String, JsonElement> entries = new TreeMap<>();
            element.getAsJsonObject().entrySet().forEach(e -> entries.put(e.getKey(), sorted(e.getValue())));
            JsonObject out = new JsonObject();
            entries.forEach(out::add);
            return out;
        }
        if (element.isJsonArray()) {
            JsonArray out = new JsonArray();
            element.getAsJsonArray().forEach(item -> out.add(sorted(item)));
            return out;
        }
        return element;
    }
}
