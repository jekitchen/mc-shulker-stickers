package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.config.ConfigLoader;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.rules.BlockedWords;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * The live config ({@code plugins/ShulkerStickers/config.json}). Read from any thread;
 * replaced as a whole on reload, so readers always see one consistent version.
 */
final class Settings {

    private record Snapshot(StickerConfig config, BlockedWords blockedWords) {}

    private final Path file;
    private final Logger logger;
    private volatile Snapshot snapshot = new Snapshot(StickerConfig.DEFAULTS, BlockedWords.compile(List.of()));

    Settings(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    /** For tests: fixed settings, no file. */
    static Settings of(StickerConfig config) {
        Settings settings = new Settings(null, Logger.getLogger("ShulkerStickers"));
        settings.snapshot = new Snapshot(config, BlockedWords.compile(config.nameBlockedWords()));
        return settings;
    }

    /** Loads the file (creating it with defaults if missing). Returns the warnings, which are also logged. */
    List<String> reload() {
        List<String> warnings = new ArrayList<>();
        StickerConfig config = StickerConfig.DEFAULTS;
        try {
            ConfigLoader.Result result = ConfigLoader.loadOrCreate(file);
            config = result.config();
            warnings.addAll(result.warnings());
        } catch (IOException e) {
            warnings.add("Could not read " + file + " (" + e.getMessage() + "); using defaults");
        }
        BlockedWords blocked = BlockedWords.compile(config.nameBlockedWords());
        warnings.addAll(blocked.warnings());
        snapshot = new Snapshot(config, blocked);
        warnings.forEach(w -> logger.warning("Config: " + w));
        return warnings;
    }

    StickerConfig config() {
        return snapshot.config();
    }

    BlockedWords blockedWords() {
        return snapshot.blockedWords();
    }
}
