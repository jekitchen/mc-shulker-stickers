package io.github.jekitchen.shulkerstickers.core;

import java.util.Optional;

/**
 * The Sticker editor's staged changes (PRD §5.2). Nothing here touches the world:
 * the platform applies {@link #staged()} on Save, or drops the session on Cancel/Esc.
 */
public final class EditorSession {

    private final BoxState original;
    private BoxState staged;

    public EditorSession(BoxState original) {
        this.original = original;
        this.staged = original;
    }

    public BoxState original() {
        return original;
    }

    public BoxState staged() {
        return staged;
    }

    public void stageColor(BoxColor color) {
        staged = staged.withColor(color);
    }

    public void stageIcon(String iconId) {
        staged = staged.withIcon(Optional.of(iconId));
    }

    public void removeIcon() {
        staged = staged.withIcon(Optional.empty());
    }

    /** A blank name stages removing the name. */
    public void stageName(String name) {
        String trimmed = name.strip();
        staged = staged.withName(trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed));
    }

    public Changes changes() {
        return new Changes(
                staged.color() != original.color(),
                !staged.iconId().equals(original.iconId()),
                !staged.name().equals(original.name()));
    }

    public boolean isDirty() {
        return !changes().isEmpty();
    }
}
