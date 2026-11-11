package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.Face;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.core.text.TextKeys;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Observer;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.Plugin;

/**
 * Checks on a real server (Paper, Folia, Canvas), enabled with
 * {@code -Dshulkerstickers.selftest=true}. Every world change runs on the region
 * scheduler, so a thread-check error on Folia/Canvas is itself a failure signal.
 * Results are logged as {@code SELFTEST PASS|FAIL <name>} and a final summary line.
 */
final class SelfTest {

    private static final Sticker ICON = new Sticker(
            Optional.of("minecraft:cobblestone"), Optional.empty(), Anchor.LID, 0, Optional.empty());

    private final Plugin plugin;
    private final Settings settings;
    private final DisplayManager displays;
    private final AtomicInteger passed = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();

    SelfTest(Plugin plugin, Settings settings, DisplayManager displays) {
        this.plugin = plugin;
        this.settings = settings;
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
                        check("selftest crashed: " + e, false);
                        plugin.getLogger().log(java.util.logging.Level.SEVERE, "SELFTEST crash", e);
                        summary();
                    }
                }));
    }

    /** Region thread. */
    private void run(World world, Location origin) {
        check("datapack tag shulker_stickers:icon_blocklist is visible to plugins",
                Bukkit.getTag(Tag.REGISTRY_ITEMS, IconItems.BLOCKLIST_TAG, Material.class) != null);
        check("bundled translations load (en_us)", Texts.has(TextKeys.EDITOR_SAVE)
                && "Save".equals(Texts.plain(Bukkit.getConsoleSender(), TextKeys.EDITOR_SAVE)));
        iconVisualTests();

        // AC-1: recolor through all 17 variants.
        Block box = origin.getBlock();
        placeDecoratedBox(box, BlockFace.EAST, ICON);
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

    /** Icon visual properties go through Minecraft's codec and back (PRD §5.3). */
    private void iconVisualTests() {
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        helmet.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(Color.fromRGB(0xFF0000)));
        helmet.editMeta(meta -> meta.lore(java.util.List.of(Component.text("secret"))));
        IconItems.Captured captured = IconItems.capture(helmet);
        plugin.getLogger().info("SELFTEST INFO dyed helmet icon_visual = " + captured.visual().orElse("<none>"));
        // Must match the Fabric GameTest byte for byte (same format on both platforms).
        check("red-dyed helmet stores exactly the Fabric string",
                captured.id().equals("minecraft:leather_helmet")
                        && captured.visual().equals(Optional.of("{\"minecraft:dyed_color\":16711680}")));

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, 1);
        check("enchanted sword stores only a glint override, like Fabric",
                IconItems.capture(sword).visual().equals(Optional.of("{\"minecraft:enchantment_glint_override\":true}")));

        Sticker sticker = new Sticker(Optional.of(captured.id()), captured.visual(), Anchor.LID, 0, Optional.empty());
        Optional<ItemStack> drawn = IconItems.render(sticker, settings.config());
        check("stored icon rebuilds into the same dyed item",
                drawn.isPresent() && drawn.get().getType() == Material.LEATHER_HELMET
                        && drawn.get().getItemMeta() instanceof LeatherArmorMeta m
                        && m.getColor().asRGB() == 0xFF0000);

        Sticker junk = new Sticker(Optional.of("minecraft:stone"), Optional.of("{\"minecraft:dyed_color\":\"red\"}"),
                Anchor.LID, 0, Optional.empty());
        check("unreadable icon visuals draw nothing instead of crashing", IconItems.render(junk, settings.config()).isEmpty());
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
        Sticker side = new Sticker(ICON.iconId(), ICON.iconVisual(), Anchor.SIDE, 0, Optional.of(Face.NORTH));
        placeDecoratedBox(box, BlockFace.UP, side);
        ShulkerBox hiddenState = (ShulkerBox) box.getState();
        StickerStore.setNameHidden(hiddenState.getPersistentDataContainer(), true);
        hiddenState.update(true, false);
        ItemStack drop = box.getDrops().stream()
                .filter(i -> Tag.SHULKER_BOXES.isTagged(i.getType()))
                .findFirst().orElse(null);
        check("block PDC reaches the drop via the datapack loot table (getDrops), incl. face and name_hidden",
                drop != null && StickerStore.read(drop.getPersistentDataContainer()).equals(Optional.of(side))
                        && StickerStore.isNameHidden(drop.getPersistentDataContainer()));
        check("drop keeps contents and name via vanilla loot (getDrops)",
                drop != null && drop.getItemMeta() != null && drop.getItemMeta().hasCustomName());
        box.breakNaturally();
        Bukkit.getRegionScheduler().runDelayed(plugin, origin, t -> {
            Optional<ItemStack> dropped = box.getWorld()
                    .getNearbyEntities(box.getLocation().add(0.5, 0.5, 0.5), 2, 2, 2, e -> e instanceof Item)
                    .stream().map(e -> ((Item) e).getItemStack())
                    .filter(i -> Tag.SHULKER_BOXES.isTagged(i.getType()))
                    .findFirst();
            check("breakNaturally drop carries the sticker",
                    dropped.isPresent() && StickerStore.read(dropped.get().getPersistentDataContainer()).equals(Optional.of(side)));
            box.getWorld().getNearbyEntities(box.getLocation(), 3, 3, 3, e -> e instanceof Item).forEach(Entity::remove);
            dispenserTest(origin);
        }, 3);
    }

    /** A dispenser-placed box gets the item's sticker, on the side facing the dispenser (PRD §6.1). */
    private void dispenserTest(Location origin) {
        Block dispenserBlock = origin.clone().add(0, 0, 4).getBlock();
        Directional data = (Directional) Material.DISPENSER.createBlockData();
        data.setFacing(BlockFace.EAST);
        dispenserBlock.setBlockData(data, false);
        Block target = dispenserBlock.getRelative(BlockFace.EAST);
        target.setType(Material.AIR, false);
        ItemStack stack = new ItemStack(Material.SHULKER_BOX);
        Sticker side = new Sticker(ICON.iconId(), ICON.iconVisual(), Anchor.SIDE, 0, Optional.empty());
        stack.editPersistentDataContainer(pdc -> StickerStore.write(pdc, side));
        Dispenser dispenser = (Dispenser) dispenserBlock.getState();
        dispenser.getSnapshotInventory().addItem(stack);
        dispenser.update(true, false);
        boolean dispensed = ((Dispenser) dispenserBlock.getState()).dispense();
        Bukkit.getRegionScheduler().runDelayed(plugin, origin, t -> {
            Optional<Sticker> placed = target.getState(false) instanceof ShulkerBox box
                    ? StickerStore.read(box.getPersistentDataContainer())
                    : Optional.empty();
            BlockFace facing = target.getBlockData() instanceof Directional d ? d.getFacing() : null;
            plugin.getLogger().info("SELFTEST INFO dispenser: dispensed=" + dispensed + " placed=" + target.getType()
                    + " facing=" + facing + " sticker=" + placed);
            check("dispenser-placed box gets the sticker on a side facing the dispenser or north",
                    placed.isPresent() && placed.get().face().isPresent()
                            && (placed.get().face().get() == Face.WEST || placed.get().face().get() == Face.NORTH));
            displayTests(origin);
        }, 4);
    }

    private void displayTests(Location origin) {
        Block box = origin.clone().add(6, 0, 0).getBlock();
        placeDecoratedBox(box, BlockFace.UP, ICON);
        displays.refresh(box);
        var spawned = displays.displaysAt(box);
        plugin.getLogger().info("SELFTEST INFO icon+name displays: " + spawned.stream()
                .map(e -> e.getType() + (e.isValid() ? "" : "(invalid)")).toList());
        check("display entities spawn from a region thread (icon + name)", spawned.size() == 2
                && spawned.stream().allMatch(e -> e instanceof Display && e.isValid()));
        check("display entities are never saved (persistent=false)", spawned.stream().noneMatch(Entity::isPersistent));
        displays.remove(BlockKey.of(box));
        check("display entities removed with the box", spawned.stream().noneMatch(Entity::isValid));

        // A hidden name isn't drawn; the icon still is.
        ShulkerBox state = (ShulkerBox) box.getState();
        StickerStore.setNameHidden(state.getPersistentDataContainer(), true);
        state.update(true, false);
        displays.refresh(box);
        var hidden = displays.displaysAt(box);
        check("a name marked name_hidden is not drawn", hidden.size() == 1 && hidden.getFirst() instanceof ItemDisplay);
        displays.remove(BlockKey.of(box));

        // A name with no sticker data goes on the lid.
        Block nameOnly = origin.clone().add(9, 0, 0).getBlock();
        Directional upright = (Directional) Material.SHULKER_BOX.createBlockData();
        upright.setFacing(BlockFace.NORTH);
        nameOnly.setBlockData(upright, false);
        ShulkerBox named = (ShulkerBox) nameOnly.getState();
        named.customName(Component.text("A very long box name that is far over the limit"));
        named.update(true, false);
        displays.refresh(nameOnly);
        var text = displays.displaysAt(nameOnly);
        check("a name without sticker data is drawn on the lid, truncated",
                text.size() == 1 && text.getFirst() instanceof TextDisplay td
                        && td.getLocation().getZ() < nameOnly.getZ()
                        && net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                .serialize(td.text()).endsWith("…"));
        // Plugin disable removes every display, including ones we no longer track.
        displays.refresh(nameOnly);
        var tracked = displays.displaysAt(nameOnly);
        var stray = nameOnly.getWorld().spawn(nameOnly.getLocation().add(0.5, 1.5, 0.5), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.getPersistentDataContainer().set(PaperKeys.DISPLAY, org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
        });
        displays.removeAll();
        // Folia/Canvas have no world-wide entity sweep; there the plugin is only disabled at
        // shutdown and displays are never saved, so strays vanish with the server.
        check("plugin disable removes tracked displays" + (DisplayManager.FOLIA ? "" : " and strays"),
                !tracked.isEmpty() && tracked.stream().noneMatch(Entity::isValid)
                        && (DisplayManager.FOLIA || !stray.isValid()) && displays.count() == 0);
        stray.remove();
        dispenserIntoOccupiedSpaceTest(origin);
    }

    /** A dispense that can't place (a box is already there) must not label the existing box. */
    private void dispenserIntoOccupiedSpaceTest(Location origin) {
        Block dispenserBlock = origin.clone().add(0, 0, 8).getBlock();
        Directional data = (Directional) Material.DISPENSER.createBlockData();
        data.setFacing(BlockFace.EAST);
        dispenserBlock.setBlockData(data, false);
        Block target = dispenserBlock.getRelative(BlockFace.EAST);
        target.setType(Material.AIR, false);
        Directional existing = (Directional) Material.BLUE_SHULKER_BOX.createBlockData();
        existing.setFacing(BlockFace.UP);
        target.setBlockData(existing, false);
        ItemStack stack = new ItemStack(Material.SHULKER_BOX);
        stack.editPersistentDataContainer(pdc -> StickerStore.write(pdc, ICON));
        Dispenser dispenser = (Dispenser) dispenserBlock.getState();
        dispenser.getSnapshotInventory().clear();
        dispenser.getSnapshotInventory().addItem(stack);
        dispenser.update(true, false);
        ((Dispenser) dispenserBlock.getState()).dispense();
        Bukkit.getRegionScheduler().runDelayed(plugin, origin, t -> {
            boolean labelled = target.getState(false) instanceof ShulkerBox box
                    && StickerStore.hasData(box.getPersistentDataContainer());
            check("a dispense into an occupied space doesn't put the sticker on the box already there", !labelled);
            summary();
        }, 4);
    }

    private void placeDecoratedBox(Block block, BlockFace facing, Sticker sticker) {
        Directional data = (Directional) Material.SHULKER_BOX.createBlockData();
        data.setFacing(facing);
        block.setType(Material.AIR, false); // a fresh block entity, whatever an earlier run left here
        block.setBlockData(data, false);
        ShulkerBox state = (ShulkerBox) block.getState();
        for (int slot = 0; slot < 27; slot++) {
            state.getSnapshotInventory().setItem(slot, new ItemStack(Material.DIAMOND, slot + 1));
        }
        state.customName(Component.text("Ores"));
        state.setLock("key");
        StickerStore.write(state.getPersistentDataContainer(), sticker);
        state.update(true, false);
    }

    private void check(String name, boolean ok) {
        (ok ? passed : failed).incrementAndGet();
        plugin.getLogger().info("SELFTEST " + (ok ? "PASS " : "FAIL ") + name);
    }

    private void summary() {
        plugin.getLogger().info("SELFTEST DONE pass=" + passed.get() + " fail=" + failed.get()
                + " server=" + Bukkit.getName() + " " + Bukkit.getVersion());
        if (Boolean.getBoolean("shulkerstickers.selftest.stop")) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> Bukkit.shutdown(), 20);
        }
    }
}
