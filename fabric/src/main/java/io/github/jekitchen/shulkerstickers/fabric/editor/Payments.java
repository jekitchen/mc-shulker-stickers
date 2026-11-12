package io.github.jekitchen.shulkerstickers.fabric.editor;

import io.github.jekitchen.shulkerstickers.core.config.Cost;
import io.github.jekitchen.shulkerstickers.core.rules.Costs;
import io.github.jekitchen.shulkerstickers.core.text.TextKeys;
import io.github.jekitchen.shulkerstickers.fabric.Text;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Charging the configured costs at Save (PRD §8). Creative players pay nothing. */
public final class Payments {

    private Payments() {
    }

    public static boolean canPay(ServerPlayer player, List<Cost> costs) {
        if (player.isCreative()) {
            return true;
        }
        if (player.experienceLevel < Costs.totalXpLevels(costs)) {
            return false;
        }
        for (Map.Entry<Item, Integer> need : itemTotals(costs).entrySet()) {
            if (countPlain(player, need.getKey()) < need.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Takes the costs; call only after {@link #canPay} returned true in the same tick. */
    public static void charge(ServerPlayer player, List<Cost> costs) {
        if (player.isCreative()) {
            return;
        }
        int levels = Costs.totalXpLevels(costs);
        if (levels > 0) {
            player.giveExperienceLevels(-levels);
        }
        itemTotals(costs).forEach((item, amount) -> takePlain(player, item, amount));
        player.getInventory().setChanged();
    }

    /** "2 XP levels, 1 × Paper", in the player's language. */
    public static String describe(ServerPlayer player, List<Cost> costs) {
        return costs.stream().map(c -> switch (c.type()) {
            case XP_LEVELS -> Text.string(player, TextKeys.COST_XP_LEVELS, c.amount());
            case ITEM -> Text.string(player, TextKeys.COST_ITEM, c.amount(),
                    item(c.itemId()).map(i -> new ItemStack(i).getHoverName().getString()).orElse(c.itemId()));
            case NONE -> "";
        }).filter(s -> !s.isEmpty()).collect(Collectors.joining(", "));
    }

    /**
     * Costs are paid only with plain items (no name, enchantments or other data) from the main
     * inventory and hotbar, never from armor or the off-hand.
     */
    private static boolean isPlain(ItemStack stack, Item item) {
        return stack.is(item) && stack.getComponentsPatch().isEmpty();
    }

    private static int countPlain(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isPlain(stack, item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void takePlain(ServerPlayer player, Item item, int amount) {
        int left = amount;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && left > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isPlain(stack, item)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
    }

    private static Map<Item, Integer> itemTotals(List<Cost> costs) {
        Map<Item, Integer> totals = new HashMap<>();
        for (Cost cost : costs) {
            if (cost.type() == Cost.Type.ITEM) {
                // An unknown item can never be paid, so it maps to a cost no one can meet.
                Item item = item(cost.itemId()).orElse(null);
                if (item == null) {
                    totals.put(net.minecraft.world.item.Items.BARRIER, Integer.MAX_VALUE);
                } else {
                    totals.merge(item, cost.amount(), Integer::sum);
                }
            }
        }
        return totals;
    }

    private static Optional<Item> item(String id) {
        Identifier parsed = id == null ? null : Identifier.tryParse(id);
        return parsed == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(parsed);
    }
}
