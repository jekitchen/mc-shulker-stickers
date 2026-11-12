package io.github.jekitchen.shulkerstickers.fabric;

import io.github.jekitchen.shulkerstickers.core.text.SafePlainText;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The plain text of a box's name, safe for untrusted names (see {@link SafePlainText}): never
 * {@code Component#getString}, which expands translation arguments without limit.
 */
public final class SafeNames {

    private SafeNames() {
    }

    public static String plain(Component name) {
        SafePlainText text = new SafePlainText();
        walk(name, text);
        return text.result();
    }

    private static void walk(Component component, SafePlainText text) {
        if (!text.visit()) {
            return;
        }
        ComponentContents contents = component.getContents();
        if (contents instanceof PlainTextContents plain) {
            text.append(plain.text());
        } else if (contents instanceof TranslatableContents translatable) {
            // The translation's own text, with its placeholders left as they are (never filled in).
            String fallback = translatable.getFallback() != null ? translatable.getFallback() : translatable.getKey();
            text.append(Language.getInstance().getOrDefault(translatable.getKey(), fallback));
        }
        for (Component sibling : component.getSiblings()) {
            walk(sibling, text);
        }
    }
}
