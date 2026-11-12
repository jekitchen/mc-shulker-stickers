package io.github.jekitchen.shulkerstickers.core.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads and writes the config file. A bad value never stops the server: it keeps its
 * default and adds a warning that the platform logs and shows on {@code reload}.
 */
public final class ConfigLoader {

    public record Result(StickerConfig config, List<String> warnings) {
        public Result {
            warnings = List.copyOf(warnings);
        }
    }

    private static final Map<String, Set<String>> KNOWN = Map.of(
            "color", Set.of("enabled", "cost"),
            "icon", Set.of("enabled", "cost", "blocklist", "max_data_bytes", "view_range", "full_bright"),
            "name", Set.of("edit_enabled", "display_enabled", "cost", "view_range", "max_display_chars", "blocked_words"),
            "display", Set.of("hide_while_open"),
            "help", Set.of("first_place_message"),
            "allow_adventure_mode", Set.of(),
            "protection", Set.of("simulate_place_event"));

    private ConfigLoader() {}

    public static Result loadOrCreate(Path file) throws IOException {
        if (Files.notExists(file)) {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, toJson(StickerConfig.DEFAULTS), StandardCharsets.UTF_8);
            return new Result(StickerConfig.DEFAULTS, List.of());
        }
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static Result parse(String json) {
        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                return new Result(StickerConfig.DEFAULTS, List.of("Config is not a JSON object; using defaults"));
            }
            root = element.getAsJsonObject();
        } catch (JsonParseException e) {
            return new Result(StickerConfig.DEFAULTS, List.of("Config is not valid JSON (" + e.getMessage() + "); using defaults"));
        }
        Reader r = new Reader(root);
        r.warnUnknown();
        StickerConfig d = StickerConfig.DEFAULTS;
        StickerConfig config = new StickerConfig(
                r.bool("color.enabled", d.colorEnabled()),
                r.bool("icon.enabled", d.iconEnabled()),
                r.bool("name.edit_enabled", d.nameEditEnabled()),
                r.bool("name.display_enabled", d.nameDisplayEnabled()),
                r.cost("color.cost"),
                r.cost("icon.cost"),
                r.cost("name.cost"),
                r.strings("icon.blocklist", d.iconBlocklist()),
                r.count("icon.max_data_bytes", d.iconMaxDataBytes()),
                r.count("icon.view_range", d.iconViewRange()),
                r.count("name.view_range", d.nameViewRange()),
                r.bool("icon.full_bright", d.iconFullBright()),
                r.count("name.max_display_chars", d.nameMaxDisplayChars()),
                r.strings("name.blocked_words", d.nameBlockedWords()),
                r.bool("display.hide_while_open", d.hideWhileOpen()),
                r.bool("help.first_place_message", d.firstPlaceMessage()),
                r.bool("allow_adventure_mode", d.allowAdventureMode()),
                r.bool("protection.simulate_place_event", d.simulatePlaceEvent()));
        return new Result(config, r.warnings);
    }

    public static String toJson(StickerConfig c) {
        JsonObject root = new JsonObject();
        JsonObject color = section(root, "color");
        color.addProperty("enabled", c.colorEnabled());
        color.add("cost", costJson(c.colorCost()));

        JsonObject icon = section(root, "icon");
        icon.addProperty("enabled", c.iconEnabled());
        icon.add("cost", costJson(c.iconCost()));
        icon.add("blocklist", array(c.iconBlocklist()));
        icon.addProperty("max_data_bytes", c.iconMaxDataBytes());
        icon.addProperty("view_range", c.iconViewRange());
        icon.addProperty("full_bright", c.iconFullBright());

        JsonObject name = section(root, "name");
        name.addProperty("edit_enabled", c.nameEditEnabled());
        name.addProperty("display_enabled", c.nameDisplayEnabled());
        name.add("cost", costJson(c.nameCost()));
        name.addProperty("view_range", c.nameViewRange());
        name.addProperty("max_display_chars", c.nameMaxDisplayChars());
        name.add("blocked_words", array(c.nameBlockedWords()));

        section(root, "display").addProperty("hide_while_open", c.hideWhileOpen());
        section(root, "help").addProperty("first_place_message", c.firstPlaceMessage());
        root.addProperty("allow_adventure_mode", c.allowAdventureMode());
        section(root, "protection").addProperty("simulate_place_event", c.simulatePlaceEvent());
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n";
    }

    private static JsonObject section(JsonObject root, String name) {
        JsonObject section = new JsonObject();
        root.add(name, section);
        return section;
    }

    private static JsonArray array(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static JsonObject costJson(Cost cost) {
        JsonObject json = new JsonObject();
        json.addProperty("type", cost.type().name().toLowerCase(Locale.ROOT));
        if (cost.type() == Cost.Type.ITEM) {
            json.addProperty("item", cost.itemId());
        }
        if (cost.type() != Cost.Type.NONE) {
            json.addProperty("amount", cost.amount());
        }
        return json;
    }

    /** Looks values up by dotted path and records a warning for each bad one. */
    private static final class Reader {
        private final JsonObject root;
        private final List<String> warnings = new ArrayList<>();

        Reader(JsonObject root) {
            this.root = root;
        }

        void warnUnknown() {
            for (String key : root.keySet()) {
                Set<String> children = KNOWN.get(key);
                if (children == null) {
                    warnings.add("Unknown setting '" + key + "' ignored");
                } else if (!children.isEmpty() && root.get(key).isJsonObject()) {
                    for (String child : root.getAsJsonObject(key).keySet()) {
                        if (!children.contains(child)) {
                            warnings.add("Unknown setting '" + key + "." + child + "' ignored");
                        }
                    }
                }
            }
        }

        private JsonElement get(String path) {
            JsonElement current = root;
            for (String part : path.split("\\.")) {
                if (current == null || !current.isJsonObject()) {
                    return null;
                }
                current = current.getAsJsonObject().get(part);
            }
            return current;
        }

        boolean bool(String path, boolean fallback) {
            JsonElement e = get(path);
            if (e == null) {
                return fallback;
            }
            if (e instanceof JsonPrimitive p && p.isBoolean()) {
                return p.getAsBoolean();
            }
            return invalid(path, "true or false", fallback);
        }

        int count(String path, int fallback) {
            JsonElement e = get(path);
            if (e == null) {
                return fallback;
            }
            if (e instanceof JsonPrimitive p && p.isNumber()) {
                double value = p.getAsDouble();
                if (value >= 0 && value <= Integer.MAX_VALUE && value == Math.rint(value)) {
                    return (int) value;
                }
            }
            return invalid(path, "a whole number of 0 or more", fallback);
        }

        List<String> strings(String path, List<String> fallback) {
            JsonElement e = get(path);
            if (e == null) {
                return fallback;
            }
            if (e.isJsonArray()) {
                List<String> values = new ArrayList<>();
                for (JsonElement item : e.getAsJsonArray()) {
                    if (!(item instanceof JsonPrimitive p) || !p.isString()) {
                        return invalid(path, "a list of strings", fallback);
                    }
                    values.add(p.getAsString());
                }
                return values;
            }
            return invalid(path, "a list of strings", fallback);
        }

        Cost cost(String path) {
            JsonElement e = get(path);
            if (e == null) {
                return Cost.NONE;
            }
            if (!e.isJsonObject()) {
                return invalid(path, "an object like {\"type\":\"none\"}", Cost.NONE);
            }
            JsonObject o = e.getAsJsonObject();
            String type = o.get("type") instanceof JsonPrimitive p && p.isString() ? p.getAsString() : "";
            Cost.Type parsed;
            try {
                parsed = Cost.Type.valueOf(type.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return invalid(path + ".type", "none, xp_levels or item", Cost.NONE);
            }
            if (parsed == Cost.Type.NONE) {
                return Cost.NONE;
            }
            int amount = 1;
            if (o.has("amount")) {
                if (o.get("amount") instanceof JsonPrimitive p && p.isNumber() && p.getAsInt() >= 1) {
                    amount = p.getAsInt();
                } else {
                    return invalid(path + ".amount", "a whole number of 1 or more", Cost.NONE);
                }
            }
            if (parsed == Cost.Type.ITEM) {
                if (!(o.get("item") instanceof JsonPrimitive p) || !p.isString() || p.getAsString().isBlank()) {
                    return invalid(path + ".item", "an item ID like \"minecraft:paper\"", Cost.NONE);
                }
                return new Cost(parsed, amount, p.getAsString());
            }
            return new Cost(parsed, amount, null);
        }

        private <T> T invalid(String path, String expected, T fallback) {
            warnings.add("Setting '" + path + "' must be " + expected + "; using the default");
            return fallback;
        }
    }
}
