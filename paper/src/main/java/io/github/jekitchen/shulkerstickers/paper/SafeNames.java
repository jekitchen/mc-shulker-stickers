package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.text.SafePlainText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;

/**
 * The plain text of a box's name, safe for untrusted names (see {@link SafePlainText}): never
 * Adventure's plain serializer, which (through Paper's flattener) expands translation arguments
 * without limit.
 */
final class SafeNames {

    private SafeNames() {
    }

    static String plain(Component name) {
        SafePlainText text = new SafePlainText();
        walk(name, text);
        return text.result();
    }

    private static void walk(Component component, SafePlainText text) {
        if (!text.visit()) {
            return;
        }
        if (component instanceof TextComponent plain) {
            text.append(plain.content());
        } else if (component instanceof TranslatableComponent translatable) {
            // Its own text, with placeholders left as they are (never filled in from its arguments).
            text.append(translatable.fallback() != null ? translatable.fallback() : translatable.key());
        }
        for (Component child : component.children()) {
            walk(child, text);
        }
    }
}
