package io.github.jekitchen.shulkerstickers.paper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.bukkit.Bukkit;
import org.bukkit.Tag;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Our built-in datapack carries stickers onto dropped boxes by overriding the 17 shulker loot
 * tables (PRD §6.2). Another datapack that overrides the same tables can win. This warns at
 * startup when a world datapack does that, and, for boxes broken by a player, notices a drop
 * that lost its sticker, puts it back, and warns once.
 */
final class LootTableGuard implements Listener {

    private static final Pattern SHULKER_TABLE =
            Pattern.compile("data/minecraft/loot_table/blocks/([a-z_]+_)?shulker_box\\.json");

    private final Plugin plugin;
    private boolean warnedAtDrop;

    LootTableGuard(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Logs a warning for each world datapack that also overrides a shulker box loot table. */
    void checkWorldDatapacks() {
        try {
            Path folder = Bukkit.getWorlds().getFirst().getWorldFolder().toPath().resolve("datapacks");
            for (String pack : overridingPacks(folder)) {
                plugin.getLogger().warning("Datapack '" + pack + "' overrides shulker box loot tables. If it loads after"
                        + " Shulker Stickers' built-in pack, broken boxes can lose their stickers from explosions"
                        + " and pistons (player breaks are repaired).");
            }
        } catch (RuntimeException e) {
            plugin.getLogger().fine("Could not scan world datapacks: " + e);
        }
    }

    /** Names of packs (folders or zips) in {@code folder} that contain a shulker box loot table. */
    static List<String> overridingPacks(Path folder) {
        List<String> found = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return found;
        }
        try (Stream<Path> packs = Files.list(folder)) {
            for (Path pack : packs.toList()) {
                if (overrides(pack)) {
                    found.add(pack.getFileName().toString());
                }
            }
        } catch (IOException e) {
            // unreadable folder: nothing to report
        }
        return found;
    }

    private static boolean overrides(Path pack) {
        try {
            if (Files.isDirectory(pack)) {
                try (Stream<Path> files = Files.walk(pack, 6)) {
                    return files.anyMatch(f -> SHULKER_TABLE.matcher(pack.relativize(f).toString().replace('\\', '/')).matches());
                }
            }
            if (pack.getFileName().toString().endsWith(".zip")) {
                try (ZipFile zip = new ZipFile(pack.toFile())) {
                    return zip.stream().anyMatch(e -> SHULKER_TABLE.matcher(e.getName()).matches());
                }
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onDrop(BlockDropItemEvent event) {
        if (!(event.getBlockState() instanceof ShulkerBox box) || !StickerStore.hasData(box.getPersistentDataContainer())) {
            return;
        }
        for (Item entity : event.getItems()) {
            ItemStack stack = entity.getItemStack();
            if (!Tag.SHULKER_BOXES.isTagged(stack.getType())) {
                continue;
            }
            boolean lost = !StickerStore.hasData(stack.getPersistentDataContainer());
            if (lost) {
                stack.editPersistentDataContainer(pdc -> StickerStore.copy(box.getPersistentDataContainer(), pdc));
                entity.setItemStack(stack);
                if (!warnedAtDrop) {
                    warnedAtDrop = true;
                    plugin.getLogger().warning("A broken shulker box dropped without its sticker, so another datapack is"
                            + " overriding the shulker box loot tables. Player breaks are repaired; explosions and"
                            + " pistons can still lose stickers.");
                }
            }
        }
    }
}
