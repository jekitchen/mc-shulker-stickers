package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.text.Translations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Server-side translation (PRD §5.2): vanilla clients don't have our language files, so
 * every string is translated into the player's language before it's sent.
 */
final class Texts {

    private static final Translations TRANSLATIONS = Translations.loadBundled();

    private Texts() {}

    static String locale(CommandSender sender) {
        return sender instanceof Player p ? p.locale().toString() : Translations.FALLBACK;
    }

    static String plain(CommandSender sender, String key, Object... args) {
        return TRANSLATIONS.translate(locale(sender), key, args);
    }

    static Component text(CommandSender sender, String key, Object... args) {
        return Component.text(plain(sender, key, args));
    }

    static Component text(CommandSender sender, TextColor color, String key, Object... args) {
        return Component.text(plain(sender, key, args), color);
    }

    /** Item names and lore are italic by default; menu buttons shouldn't be. */
    static Component label(CommandSender sender, String key, Object... args) {
        return Component.text(plain(sender, key, args)).decoration(TextDecoration.ITALIC, false);
    }

    static Component lore(CommandSender sender, String key, Object... args) {
        return Component.text(plain(sender, key, args), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    static boolean has(String key) {
        return TRANSLATIONS.has(Translations.FALLBACK, key);
    }
}
