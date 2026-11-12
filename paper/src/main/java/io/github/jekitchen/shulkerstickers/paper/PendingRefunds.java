package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.config.Cost;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Refunds owed to players who left before their Save finished; paid when they next join.
 * Kept in memory only, so a refund owed across a server restart is lost. That needs a
 * player to log out in the one or two ticks between Save and its result, on a Save that
 * was then refused, so 0.1 accepts it.
 */
final class PendingRefunds {

    private final Map<UUID, List<Cost>> owed = new ConcurrentHashMap<>();

    void owe(UUID player, List<Cost> costs) {
        if (costs.isEmpty()) {
            return;
        }
        owed.merge(player, List.copyOf(costs), (a, b) -> {
            List<Cost> all = new ArrayList<>(a);
            all.addAll(b);
            return List.copyOf(all);
        });
    }

    /** Removes and returns what the player is owed. */
    List<Cost> take(UUID player) {
        List<Cost> costs = owed.remove(player);
        return costs == null ? List.of() : costs;
    }
}
