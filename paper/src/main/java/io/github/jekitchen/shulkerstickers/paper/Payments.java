package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.config.Cost;
import io.github.jekitchen.shulkerstickers.core.rules.Costs;
import io.github.jekitchen.shulkerstickers.core.text.TextKeys;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Charges the Save costs from PRD §8. Player thread only. Creative players pay nothing. */
final class Payments {

    private Payments() {}

    static List<Cost> costsFor(Player player, List<Cost> costs) {
        return player.getGameMode() == GameMode.CREATIVE ? List.of() : costs;
    }

    static boolean canPay(Player player, List<Cost> costs) {
        if (player.getLevel() < Costs.totalXpLevels(costs)) {
            return false;
        }
        for (Map.Entry<Material, Integer> e : itemTotals(costs).entrySet()) {
            if (e.getKey() == null || !player.getInventory().containsAtLeast(new ItemStack(e.getKey()), e.getValue())) {
                return false;
            }
        }
        return true;
    }

    static void charge(Player player, List<Cost> costs) {
        player.setLevel(player.getLevel() - Costs.totalXpLevels(costs));
        itemTotals(costs).forEach((material, amount) -> player.getInventory().removeItem(new ItemStack(material, amount)));
    }

    /** Gives a charge back, dropping what doesn't fit at the player's feet. */
    static void refund(Player player, List<Cost> costs) {
        player.setLevel(player.getLevel() + Costs.totalXpLevels(costs));
        itemTotals(costs).forEach((material, amount) -> player.getInventory()
                .addItem(new ItemStack(material, amount))
                .values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left)));
    }

    static String describe(Player player, List<Cost> costs) {
        return costs.stream().map(c -> switch (c.type()) {
            case XP_LEVELS -> Texts.plain(player, TextKeys.COST_XP_LEVELS, c.amount());
            case ITEM -> Texts.plain(player, TextKeys.COST_ITEM, c.amount(), c.itemId());
            case NONE -> "";
        }).filter(s -> !s.isEmpty()).collect(Collectors.joining(", "));
    }

    private static Map<Material, Integer> itemTotals(List<Cost> costs) {
        Map<Material, Integer> totals = new HashMap<>();
        for (Cost cost : costs) {
            if (cost.type() == Cost.Type.ITEM) {
                totals.merge(Material.matchMaterial(cost.itemId()), cost.amount(), Integer::sum);
            }
        }
        return totals;
    }
}
