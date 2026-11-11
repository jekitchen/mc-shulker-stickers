package io.github.jekitchen.shulkerstickers.fabric.editor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** One editor per box at a time (PRD §5.2 Locking). Other players can still open the box normally. */
public final class EditorLocks {

    private record Holder(UUID id, String name) {}

    private static final Map<GlobalPos, Holder> LOCKS = new ConcurrentHashMap<>();

    private EditorLocks() {
    }

    /** Takes the lock for {@code player}; returns the other editor's name if someone else holds it. */
    public static Optional<String> tryAcquire(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Holder mine = new Holder(player.getUUID(), player.getGameProfile().name());
        Holder holder = LOCKS.merge(key(level, pos), mine, (old, ignored) -> old);
        return holder.id().equals(player.getUUID()) ? Optional.empty() : Optional.of(holder.name());
    }

    public static boolean isHeldBy(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Holder holder = LOCKS.get(key(level, pos));
        return holder != null && holder.id().equals(player.getUUID());
    }

    public static void release(ServerLevel level, BlockPos pos, ServerPlayer player) {
        LOCKS.computeIfPresent(key(level, pos), (k, h) -> h.id().equals(player.getUUID()) ? null : h);
    }

    public static void releaseAll(UUID playerId) {
        LOCKS.values().removeIf(h -> h.id().equals(playerId));
    }

    private static GlobalPos key(ServerLevel level, BlockPos pos) {
        return GlobalPos.of(level.dimension(), pos.immutable());
    }
}
