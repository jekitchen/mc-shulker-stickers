package io.github.jekitchen.shulkerstickers.core.config;

import java.util.List;

/** The settings from PRD §8; the same schema on every platform. */
public record StickerConfig(
        boolean colorEnabled,
        boolean iconEnabled,
        boolean nameEditEnabled,
        boolean nameDisplayEnabled,
        Cost colorCost,
        Cost iconCost,
        Cost nameCost,
        List<String> iconBlocklist,
        int iconMaxDataBytes,
        int iconViewRange,
        int nameViewRange,
        boolean iconFullBright,
        int nameMaxDisplayChars,
        List<String> nameBlockedWords,
        boolean hideWhileOpen,
        boolean firstPlaceMessage,
        boolean allowAdventureMode,
        boolean simulatePlaceEvent) {

    public static final StickerConfig DEFAULTS = new StickerConfig(
            true, true, true, true,
            Cost.NONE, Cost.NONE, Cost.NONE,
            List.of(), 2048, 32, 10, false, 24, List.of(),
            true, true, false, false);

    public StickerConfig {
        iconBlocklist = List.copyOf(iconBlocklist);
        nameBlockedWords = List.copyOf(nameBlockedWords);
    }
}
