package io.github.jekitchen.shulkerstickers.fabric;

import io.github.jekitchen.shulkerstickers.core.config.ConfigLoader;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.rules.BlockedWords;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/** The loaded config ({@code config/shulker_stickers.json}), swapped atomically on reload. */
public final class Settings {

    public record Loaded(StickerConfig config, BlockedWords blockedWords) {
        public static Loaded of(StickerConfig config) {
            return new Loaded(config, BlockedWords.compile(config.nameBlockedWords()));
        }
    }

    private static volatile Loaded current = Loaded.of(StickerConfig.DEFAULTS);

    private Settings() {
    }

    public static StickerConfig config() {
        return current.config();
    }

    public static Loaded loaded() {
        return current;
    }

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(ShulkerStickersFabric.MOD_ID + ".json");
    }

    /** Reads the config file (creating it with defaults if missing) and returns any warnings. */
    public static List<String> load() {
        List<String> warnings = new ArrayList<>();
        try {
            ConfigLoader.Result result = ConfigLoader.loadOrCreate(file());
            Loaded loaded = Loaded.of(result.config());
            warnings.addAll(result.warnings());
            warnings.addAll(loaded.blockedWords().warnings());
            current = loaded;
        } catch (IOException e) {
            warnings.add("Could not read " + file() + " (" + e.getMessage() + "); keeping the current settings");
        }
        warnings.forEach(w -> ShulkerStickersFabric.LOGGER.warn("Config: {}", w));
        return warnings;
    }
}
