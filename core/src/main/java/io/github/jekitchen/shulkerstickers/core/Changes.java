package io.github.jekitchen.shulkerstickers.core;

/** Which parts of a box a Save would change. Costs are charged only for these. */
public record Changes(boolean color, boolean icon, boolean name) {

    public boolean isEmpty() {
        return !color && !icon && !name;
    }
}
