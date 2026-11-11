package io.github.jekitchen.shulkerstickers.fabric.data;

import io.github.jekitchen.shulkerstickers.core.Anchor;

/**
 * A box's icon as stored in {@code custom_data}. {@code iconVisual} holds the allowed visual
 * properties as SNBT (PRD §5.3); the spike leaves it empty.
 */
public record Sticker(String iconId, String iconVisual, Anchor anchor, int rot) {
}
