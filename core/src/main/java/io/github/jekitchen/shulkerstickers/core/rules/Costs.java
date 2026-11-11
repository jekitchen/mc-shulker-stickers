package io.github.jekitchen.shulkerstickers.core.rules;

import io.github.jekitchen.shulkerstickers.core.Changes;
import io.github.jekitchen.shulkerstickers.core.config.Cost;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import java.util.ArrayList;
import java.util.List;

/** What a Save costs: only the parts that changed are charged (PRD §8). */
public final class Costs {

    private Costs() {}

    /** Costs for color, icon and name, in that order, leaving out free ones. */
    public static List<Cost> forChanges(Changes changes, StickerConfig config) {
        List<Cost> costs = new ArrayList<>();
        if (changes.color() && !config.colorCost().isFree()) {
            costs.add(config.colorCost());
        }
        if (changes.icon() && !config.iconCost().isFree()) {
            costs.add(config.iconCost());
        }
        if (changes.name() && !config.nameCost().isFree()) {
            costs.add(config.nameCost());
        }
        return costs;
    }

    public static int totalXpLevels(List<Cost> costs) {
        return costs.stream().filter(c -> c.type() == Cost.Type.XP_LEVELS).mapToInt(Cost::amount).sum();
    }
}
