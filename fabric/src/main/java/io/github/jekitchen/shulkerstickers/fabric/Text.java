package io.github.jekitchen.shulkerstickers.fabric;

import io.github.jekitchen.shulkerstickers.core.text.Translations;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side translation (PRD §5.2): vanilla clients don't have our language files, so text is
 * translated into the player's language before it is sent.
 */
public final class Text {

    public static final Translations TRANSLATIONS = Translations.loadBundled();

    private Text() {
    }

    public static String lang(ServerPlayer player) {
        return player.clientInformation().language();
    }

    public static String string(ServerPlayer player, String key, Object... args) {
        return TRANSLATIONS.translate(lang(player), key, args);
    }

    public static MutableComponent tr(ServerPlayer player, String key, Object... args) {
        return Component.literal(string(player, key, args));
    }

    public static MutableComponent tr(CommandSourceStack source, String key, Object... args) {
        ServerPlayer player = source.getPlayer();
        String lang = player == null ? Translations.FALLBACK : lang(player);
        return Component.literal(TRANSLATIONS.translate(lang, key, args));
    }

    /** White, non-italic text for item names and lore in menus. */
    public static MutableComponent plain(String text) {
        return Component.literal(text).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.WHITE));
    }

    public static MutableComponent grey(String text) {
        return Component.literal(text).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY));
    }
}
