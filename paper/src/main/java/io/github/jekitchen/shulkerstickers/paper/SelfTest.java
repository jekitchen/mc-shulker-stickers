package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.BoxColor;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Observer;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Spike-only checks on a real server (Paper, Folia, Canvas), enabled with
 * {@code -Dshulkerstickers.selftest=true}. Every world change runs on the region
 * scheduler, so a thread-check error on Folia/Canvas is itself a failure signal.
 * Results are logged as {@code SELFTEST PASS|FAIL <name>} and a final summary line.
 */
final class SelfTest {

    private static final StickerData ICON = new StickerData("minecraft:cobblestone", "{}", Anchor.LID, 0);

    private final Plugin plugin;
    private final DisplayManager displays;
    private final AtomicInteger passed = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();

    SelfTest(Plugin plugin, DisplayManager displays) {
        this.plugin = plugin;
        this.displays = displays;
    }

    void schedule() {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> start(), 40);
    }

    private void start() {
        World world = Bukkit.getWorlds().getFirst();
        Location origin = new Location(world, 8, 250, 8);
        world.getChunkAtAsync(origin).thenAccept(chunk ->
                Bukkit.getRegionScheduler().execute(plugin, origin, () -> {
                    chunk.addPluginChunkTicket(plugin);
                    try {
                        run(world, origin);
                    } catch (Throwable e) {
                        fail("selftest crashed: " + e);
                        plugin.getLogger().log(java.util.logging.Level.SEVERE, "SELFTEST crash", e);
                        summary();
                    }
                }));
    }

    /** Region thread. */
    private void run(World world, Location origin) {
        check("datapack tag shulker_stickers:icon_blocklist is visible to plugins",
                Bukkit.getTag(Tag.REGISTRY_ITEMS, new NamespacedKey("shulker_stickers", "icon_blocklist"),
                        Material.class) != null);

        // AC-1: recolor through all 17 variants.
        Block box = origin.getBlock();
        placeDecoratedBox(box, BlockFace.EAST);
        boolean ok = true;
        String why = "";
        for (BoxColor color : BoxColor.values()) {
            BoxRecolorer.recolor(box, color);
            ShulkerBox state = (ShulkerBox) box.getState();
            boolean variantOk = box.getType() == Material.matchMaterial(color.itemId())
                    && ((Directional) box.getBlockData()).getFacing() == BlockFace.EAST
                    && Component.text("Ores").equals(state.customName())
                    && "key".equals(state.getLock())
                    && StickerStore.read(state.getPersistentDataContainer()).equals(Optional.of(ICON));
            for (int slot = 0; slot < 27 && variantOk; slot++) {
                ItemStack item = state.getInventory().getItem(slot);
                variantOk = item != null && item.getType() == Material.DIAMOND && item.getAmount() == slot + 1;
            }
            if (!variantOk) {
                ok = false;
                why = color.name();
                break;
            }
        }
        check("AC-1 recolor keeps 27 slots, name, lock, icon, facing (all 17 variants)" + (ok ? "" : " failed at " + why), ok);
        check("AC-1 recolor drops no item entities",
                box.getWorld().getNearbyEntities(box.getLocation(), 4, 4, 4, e -> e instanceof Item).isEmpty());

        // AC-16: an observer watching the box must not pulse on recolor.
        Block observerBlock = box.getRelative(BlockFace.UP);
        Observer observer = (Observer) Material.OBSERVER.createBlockData();
        observer.setFacing(BlockFace.DOWN);
        observerBlock.setBlockData(observer, false);
        Bukkit.getRegionScheduler().runDelayed(plugin, origin, t1 -> {
            AtomicBoolean pulsed = new AtomicBoolean(false);
            BoxRecolorer.recolor(box, BoxColor.RED);
            watchObserver(origin, observerBlock, pulsed, 6, () -> {
                check("AC-16 recolor does not pulse an observer", !pulsed.get());
                // Control: a normal block change with physics on must pulse it.
                AtomicBoolean controlPulsed = new AtomicBoolean(false);
                Directional data = (Directional) Material.BLUE_SHULKER_BOX.createBlockData();
                data.setFacing(BlockFace.EAST);
                box.setBlockData(data, true);
                watchObserver(origin, observerBlock, controlPulsed, 6, () -> {
                    check("AC-16 control: a physics block change does pulse the observer", controlPulsed.get());
                    dropTests(origin);
                });
            });
        }, 5);
    }

    private void watchObserver(Location at, Block observerBlock, AtomicBoolean pulsed, int ticks, Runnable then) {
        Bukkit.getRegionScheduler().runAtFixedRate(plugin, at, new java.util.function.Consumer<>() {
            int left = ticks;

            @Override
            public void accept(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
                if (((Observer) observerBlock.getBlockData()).isPowered()) {
                    pulsed.set(true);
                }
                if (--left <= 0) {
                    task.cancel();
                    then.run();
                }
            }
        }, 1, 1);
    }

    /** Sticker data must survive drops (PRD §6.2 first row). */
    private void dropTests(Location origin) {
        Block box = origin.clone().add(3, 0, 0).getBlock();
        placeDecoratedBox(box, BlockFace.UP);
        ItemStack drop = box.getDrops().stream()
                .filter(i -> Tag.SHULKER_BOXES.isTagged(i.getType()))
                .findFirst().orElse(null);
        check("block PDC reaches the drop via the datapack loot table (getDrops)",
                drop != null && StickerStore.read(drop.getPersistentDataContainer()).equals(Optional.of(ICON)));
        check("drop keeps contents and name via vanilla loot (getDrops)",
                drop != null && drop.getItemMeta() != null
                        && drop.getItemMeta().hasCustomName());
        box.breakNaturally();
        Bukkit.getRegionScheduler().runDelayed(plugin, origin, t -> {
            Optional<ItemStack> dropped = box.getWorld()
                    .getNearbyEntities(box.getLocation().add(0.5, 0.5, 0.5), 2, 2, 2, e -> e instanceof Item)
                    .stream().map(e -> ((Item) e).getItemStack())
                    .filter(i -> Tag.SHULKER_BOXES.isTagged(i.getType()))
                    .findFirst();
            check("breakNaturally drop carries the sticker",
                    dropped.isPresent() && StickerStore.read(dropped.get().getPersistentDataContainer())
                            .equals(Optional.of(ICON)));
            box.getWorld().getNearbyEntities(box.getLocation(), 3, 3, 3, e -> e instanceof Item)
                    .forEach(Entity::remove);
            displayTests(origin);
        }, 3);
    }

    private void displayTests(Location origin) {
        Block box = origin.clone().add(6, 0, 0).getBlock();
        placeDecoratedBox(box, BlockFace.UP);
        displays.refresh(box);
        var spawned = displays.displaysAt(box);
        check("display entities spawn from a region thread (icon + name)", spawned.size() == 2
                && spawned.stream().allMatch(e -> e instanceof Display && e.isValid()));
        check("display entities are never saved (persistent=false)",
                spawned.stream().noneMatch(Entity::isPersistent));
        displays.remove(BlockKey.of(box));
        check("display entities removed with the box", spawned.stream().noneMatch(Entity::isValid));
        summary();
    }

    private void placeDecoratedBox(Block block, BlockFace facing) {
        Directional data = (Directional) Material.SHULKER_BOX.createBlockData();
        data.setFacing(facing);
        block.setBlockData(data, false);
        ShulkerBox state = (ShulkerBox) block.getState();
        for (int slot = 0; slot < 27; slot++) {
            state.getSnapshotInventory().setItem(slot, new ItemStack(Material.DIAMOND, slot + 1));
        }
        state.customName(Component.text("Ores"));
        state.setLock("key");
        StickerStore.write(state.getPersistentDataContainer(), ICON);
        state.update(true, false);
    }

    private void check(String name, boolean ok) {
        (ok ? passed : failed).incrementAndGet();
        plugin.getLogger().info("SELFTEST " + (ok ? "PASS " : "FAIL ") + name);
    }

    private void fail(String name) {
        check(name, false);
    }

    private void summary() {
        plugin.getLogger().info("SELFTEST DONE pass=" + passed.get() + " fail=" + failed.get()
                + " server=" + Bukkit.getName() + " " + Bukkit.getVersion());
        if (Boolean.getBoolean("shulkerstickers.selftest.stop")) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> Bukkit.shutdown(), 20);
        }
    }
}
