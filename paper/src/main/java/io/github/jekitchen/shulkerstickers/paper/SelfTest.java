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
import org.bukkit.block.data.BlockData;
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
    private final EditorService editor;
    private final AtomicInteger passed = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();

    SelfTest(Plugin plugin, Settings settings, DisplayManager displays, EditorService editor) {
        this.plugin = plugin;
        this.settings = settings;
        this.displays = displays;
        this.editor = editor;
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
        // Headless: no player is ever nearby, so display checks force "a player is nearby".
        displays.forceNearby(true);
        check("datapack tag shulker_stickers:icon_blocklist is visible to plugins",
                Bukkit.getTag(Tag.REGISTRY_ITEMS, IconItems.BLOCKLIST_TAG, Material.class) != null);
        check("bundled translations load (en_us)", Texts.has(TextKeys.EDITOR_SAVE)
                && "Save".equals(Texts.plain(Bukkit.getConsoleSender(), TextKeys.EDITOR_SAVE)));
        iconVisualTests();

        // AC-1: recolor through all 17 variants.
        Block box = origin.getBlock();
        placeDecoratedBox(box, BlockFace.EAST, ICON);
        lockKeyTests(box);
        migrationTests(box);
        claimTests(box);
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
            // An observer fires 2 ticks after the change and stays on for 2; watch well past that.
            watchObserver(origin, observerBlock, pulsed, 10, () -> {
                check("AC-16 recolor does not pulse an observer", !pulsed.get());
                AtomicBoolean controlPulsed = new AtomicBoolean(false);
                String before = observerBlock.getBlockData().getAsString() + " above " + box.getType();
                Directional data = (Directional) Material.BLUE_SHULKER_BOX.createBlockData();
                data.setFacing(BlockFace.EAST);
                box.setBlockData(data, true);
                watchObserver(origin, observerBlock, controlPulsed, 20, () -> {
                    check("AC-16 control: a physics block change does pulse the observer"
                            + (controlPulsed.get() ? "" : " (before: " + before + "; after: "
                                    + observerBlock.getBlockData().getAsString() + " above " + box.getType() + ")"),
                            controlPulsed.get());
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

        // Security: an item_model can't make an allowed item look like a blocklisted one.
        io.github.jekitchen.shulkerstickers.core.config.StickerConfig blocksDirt = io.github.jekitchen.shulkerstickers.core.config.ConfigLoader
                .parse("{\"icon\":{\"blocklist\":[\"minecraft:dirt\"]}}").config();
        Sticker disguised = new Sticker(Optional.of("minecraft:stone"), Optional.of("{\"minecraft:item_model\":\"minecraft:dirt\"}"),
                Anchor.LID, 0, Optional.empty());
        Sticker harmless = new Sticker(Optional.of("minecraft:stone"), Optional.of("{\"minecraft:item_model\":\"minecraft:cobblestone\"}"),
                Anchor.LID, 0, Optional.empty());
        check("an item_model naming a blocklisted item isn't drawn; an allowed one is",
                IconItems.render(disguised, blocksDirt).isEmpty() && IconItems.render(harmless, blocksDirt).isPresent());
        // Security: capturing the off-hand item keeps only visual components, however much else it carries.
        ItemStack heavy = new ItemStack(Material.LEATHER_HELMET);
        heavy.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(Color.fromRGB(0xFF0000)));
        heavy.editMeta(meta -> meta.lore(java.util.Collections.nCopies(200, Component.text("x".repeat(100)))));
        check("capturing an item keeps only its visual components",
                IconItems.capture(heavy).visual().equals(Optional.of("{\"minecraft:dyed_color\":16711680}")));
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
            // §6.2 "Dispenser places box: displays attached". The sticker has an icon and no name.
            var shown = displays.displaysAt(target);
            check("dispenser-placed box gets its icon display" + (shown.size() == 1 ? "" : " (got " + shown + ")"),
                    shown.size() == 1 && shown.getFirst() instanceof ItemDisplay && shown.getFirst().isValid());
            displays.forget(BlockKey.of(target));
            displayTests(origin);
        }, 4);
    }

    private void displayTests(Location origin) {
        Block box = origin.clone().add(6, 0, 0).getBlock();
        placeDecoratedBox(box, BlockFace.UP, ICON);
        // AC-8: displays exist only while a player is within view range.
        displays.forceNearby(false);
        displays.refresh(box);
        check("with no player nearby no display entities are spawned, but the box is tracked",
                displays.displaysAt(box).isEmpty() && displays.isTracked(box));
        displays.forceNearby(true);
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
        // Orientation convention: on the north-facing lid the text faces north and reads upright.
        boolean facesOut = text.size() == 1 && facesOutUpright(text.getFirst(), new org.joml.Vector3f(0, 0, -1));
        check("a name on a north lid faces north and reads upright", facesOut);
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

    /** The display's image normal (+Z turned) points along {@code normal} and its top (+Y turned) points up. */
    private static boolean facesOutUpright(Entity entity, org.joml.Vector3f normal) {
        if (!(entity instanceof Display display)) {
            return false;
        }
        org.joml.Quaternionf q = display.getTransformation().getLeftRotation();
        org.joml.Vector3f n = q.transform(new org.joml.Vector3f(0, 0, 1));
        org.joml.Vector3f up = q.transform(new org.joml.Vector3f(0, 1, 0));
        return n.distance(normal) < 1e-3 && up.distance(new org.joml.Vector3f(0, 1, 0)) < 1e-3
                && display.getLocation().getYaw() == 0f && display.getLocation().getPitch() == 0f;
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
            hopperRecolorTest(origin);
        }, 4);
    }

    // ---- release-gate and persistence-matrix checks, below the origin in the same chunk -------

    /** 27 different items, so a duplicated or lost stack shows up by type. */
    private static final Material[] DISTINCT = {
        Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.IRON_INGOT, Material.COAL,
        Material.REDSTONE, Material.LAPIS_LAZULI, Material.QUARTZ, Material.STICK, Material.STONE,
        Material.DIRT, Material.COBBLESTONE, Material.OAK_LOG, Material.SAND, Material.GRAVEL,
        Material.CLAY_BALL, Material.BRICK, Material.BONE, Material.STRING, Material.FEATHER,
        Material.FLINT, Material.LEATHER, Material.PAPER, Material.BOOK, Material.WHEAT,
        Material.APPLE, Material.BREAD,
    };

    private static final int AC2_TICKS = 100;

    /**
     * AC-2 (release gate): recolors while a hopper pulls from a box, and while another feeds
     * into one, neither duplicate nor lose items. The boxes are recolored on every tick, so
     * every tick a hopper moves an item has a recolor too; the item totals are checked each tick.
     */
    private void hopperRecolorTest(Location origin) {
        Block pullBox = origin.clone().add(-5, -10, -5).getBlock();
        Block pullHopper = pullBox.getRelative(BlockFace.DOWN);
        Block chest = pullHopper.getRelative(BlockFace.EAST);
        Block feedBox = origin.clone().add(-5, -10, 1).getBlock();
        Block feedHopper = feedBox.getRelative(BlockFace.UP);
        for (Block b : java.util.List.of(pullBox, pullHopper, chest, feedBox, feedHopper)) {
            b.setType(Material.AIR, false);
        }
        removeItemsNear(pullBox, 4);
        removeItemsNear(feedBox, 4);
        java.util.Set<java.util.UUID> oldItems = itemIds(pullBox, 16);

        // A box full of distinct stacks over a hopper that empties into a chest.
        ItemStack[] full = new ItemStack[27];
        for (int slot = 0; slot < 27; slot++) {
            full[slot] = new ItemStack(DISTINCT[slot], slot + 1);
        }
        placeStickeredBox(pullBox, full);
        placeHopper(pullHopper, BlockFace.EAST);
        chest.setType(Material.CHEST, false);
        // A hopper above a box with room, holding five distinct stacks.
        placeStickeredBox(feedBox, new ItemStack(Material.NETHERITE_SCRAP, 3));
        placeHopper(feedHopper, BlockFace.DOWN,
                new ItemStack(Material.AMETHYST_SHARD, 4), new ItemStack(Material.COPPER_INGOT, 5),
                new ItemStack(Material.PRISMARINE_SHARD, 6), new ItemStack(Material.BLAZE_ROD, 7),
                new ItemStack(Material.SLIME_BALL, 8));
        Block[] pullSide = {pullBox, pullHopper, chest};
        Block[] feedSide = {feedBox, feedHopper};
        java.util.Map<Material, Integer> pullExpected = count(pullSide);
        java.util.Map<Material, Integer> feedExpected = count(feedSide);
        int feedStart = total(feedHopper);

        Bukkit.getRegionScheduler().runAtFixedRate(plugin, origin, new java.util.function.Consumer<>() {
            int tick;
            int pulled;
            int fedLeft = feedStart;
            int pullTransfers;
            int feedTransfers;
            String pullMismatch;
            String feedMismatch;

            @Override
            public void accept(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
                // What the hoppers did since the last run of this task...
                var pullNow = count(pullSide);
                var feedNow = count(feedSide);
                if (pullMismatch == null && !pullNow.equals(pullExpected)) {
                    pullMismatch = "tick " + tick + ": " + difference(pullExpected, pullNow);
                }
                if (feedMismatch == null && !feedNow.equals(feedExpected)) {
                    feedMismatch = "tick " + tick + ": " + difference(feedExpected, feedNow);
                }
                int pulledNow = total(pullHopper) + total(chest);
                if (pulledNow != pulled) {
                    pullTransfers++;
                    pulled = pulledNow;
                }
                int fedLeftNow = total(feedHopper);
                if (fedLeftNow != fedLeft) {
                    feedTransfers++;
                    fedLeft = fedLeftNow;
                }
                // ...then recolor both boxes, every tick, to a color they don't have.
                BoxColor color = BoxColor.values()[(tick + 1) % BoxColor.values().length];
                BoxRecolorer.recolor(pullBox, color);
                BoxRecolorer.recolor(feedBox, color);
                if (++tick < AC2_TICKS) {
                    return;
                }
                task.cancel();
                var pullEnd = count(pullSide);
                var feedEnd = count(feedSide);
                boolean noDrops = newItemsNear(pullBox, 4, oldItems).isEmpty() && newItemsNear(feedBox, 4, oldItems).isEmpty();
                plugin.getLogger().info("SELFTEST INFO AC-2: " + AC2_TICKS + " recolors; hopper pulled " + pulled
                        + " items in " + pullTransfers + " transfers, fed " + (feedStart - fedLeft) + " items in "
                        + feedTransfers + " transfers");
                boolean pullOk = pullMismatch == null && pullEnd.equals(pullExpected);
                check("AC-2 recolor while a hopper pulls from the box: no item duplicated or lost" + (pullOk ? ""
                                : " (first at " + pullMismatch + "; at the end: " + difference(pullExpected, pullEnd) + ")"),
                        pullOk);
                boolean feedOk = feedMismatch == null && feedEnd.equals(feedExpected);
                check("AC-2 recolor while a hopper feeds the box: no item duplicated or lost" + (feedOk ? ""
                                : " (first at " + feedMismatch + "; at the end: " + difference(feedExpected, feedEnd) + ")"),
                        feedOk);
                check("AC-2 the hoppers really moved items during the recolors (pulled " + pullTransfers
                        + "x, fed " + feedTransfers + "x)", pullTransfers >= 5 && feedTransfers >= 5);
                check("AC-2 no item entities dropped, and the box keeps its sticker",
                        noDrops && StickerStore.read(((ShulkerBox) pullBox.getState()).getPersistentDataContainer())
                                .equals(Optional.of(ICON)));
                comparatorTest(origin);
            }
        }, 1, 1);
    }

    /**
     * AC-16: a comparator reading a box shows the same, correct signal before and after a
     * recolor, and doesn't flicker in between.
     */
    private void comparatorTest(Location origin) {
        Block box = origin.clone().add(-5, -15, -5).getBlock();
        Block comparator = box.getRelative(BlockFace.EAST);
        Block dust = comparator.getRelative(BlockFace.EAST);
        comparator.setType(Material.AIR, false);
        dust.setType(Material.AIR, false);
        comparator.getRelative(BlockFace.DOWN).setType(Material.STONE, false);
        dust.getRelative(BlockFace.DOWN).setType(Material.STONE, false);
        placeDecoratedBox(box, BlockFace.UP, ICON);
        int expected = comparatorSignal(((ShulkerBox) box.getState()).getInventory());
        org.bukkit.block.data.type.Comparator data =
                (org.bukkit.block.data.type.Comparator) Material.COMPARATOR.createBlockData();
        data.setFacing(BlockFace.WEST); // the input side: the box
        comparator.setBlockData(data, true);
        dust.setType(Material.REDSTONE_WIRE, true);
        Bukkit.getRegionScheduler().runDelayed(plugin, origin, t1 -> {
            int before = power(dust);
            check("AC-16 comparator reads the box before a recolor (" + before + ", expected " + expected + ")",
                    before == expected);
            BoxRecolorer.recolor(box, BoxColor.PURPLE);
            java.util.Set<Integer> seen = new java.util.TreeSet<>();
            seen.add(power(dust));
            Bukkit.getRegionScheduler().runAtFixedRate(plugin, origin, new java.util.function.Consumer<>() {
                int left = 20;

                @Override
                public void accept(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
                    seen.add(power(dust));
                    if (--left > 0) {
                        return;
                    }
                    task.cancel();
                    check("AC-16 comparator reads the right signal after a recolor, with no flicker (seen "
                            + seen + ", expected " + expected + ")",
                            seen.equals(java.util.Set.of(expected)) && box.getType() == Material.PURPLE_SHULKER_BOX);
                    comparatorUpdateProbe(origin, box, dust);
                }
            }, 1, 1);
        }, 10);
    }

    /**
     * Not a check: finds out whether a recolor itself refreshes comparators. The comparator and
     * dust are put back without physics, so the comparator hasn't read the box yet (0); a recolor
     * then either makes it read the box or leaves it at 0.
     */
    private void comparatorUpdateProbe(Location origin, Block box, Block dust) {
        Block comparator = box.getRelative(BlockFace.EAST);
        BlockData comparatorData = comparator.getBlockData();
        comparator.setType(Material.AIR, false);
        dust.setType(Material.AIR, false);
        comparator.setBlockData(comparatorData, false);
        dust.setType(Material.REDSTONE_WIRE, false);
        int fresh = comparatorSignal(((ShulkerBox) box.getState()).getInventory());
        Bukkit.getRegionScheduler().runDelayed(plugin, origin, t1 -> {
            int stale = power(dust);
            BoxRecolorer.recolor(box, BoxColor.ORANGE);
            Bukkit.getRegionScheduler().runDelayed(plugin, origin, t2 -> {
                int afterRecolor = power(dust);
                String verdict = stale == fresh ? "inconclusive (the comparator read the box when placed)"
                        : afterRecolor == fresh ? "yes" : afterRecolor == stale ? "no" : "unexpected";
                plugin.getLogger().info("SELFTEST INFO recolor refreshes comparators: " + verdict + " (dust "
                        + stale + " before the recolor, " + afterRecolor + " after; the box gives " + fresh + ")");
                pistonTest(origin);
            }, 6);
        }, 6);
    }

    /** What a comparator reads from a container (vanilla's getRedstoneSignalFromContainer). */
    private static int comparatorSignal(org.bukkit.inventory.Inventory inventory) {
        float fill = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && !stack.isEmpty()) {
                fill += stack.getAmount() / (float) Math.min(inventory.getMaxStackSize(), stack.getMaxStackSize());
            }
        }
        fill /= inventory.getSize();
        return (int) Math.floor(fill * 14) + (fill > 0 ? 1 : 0);
    }

    private static int power(Block dust) {
        return dust.getBlockData() instanceof org.bukkit.block.data.type.RedstoneWire wire ? wire.getPower() : -1;
    }

    /** §6.2 "Piston pushes box": the box breaks and its drop carries the sticker. */
    private void pistonTest(Location origin) {
        if (claimsRun()) {
            // GriefPrevention's defaults (PistonMovement: CLAIMS_ONLY, BlockSurfaceOtherExplosions) stop
            // both on purpose; runServer, runFolia and runCanvas cover these rows.
            plugin.getLogger().info("SELFTEST INFO piston and explosion drops skipped: GriefPrevention blocks"
                    + " unclaimed piston movement and surface explosions");
            itemTransferTests(origin);
            return;
        }
        Block piston = origin.clone().add(-5, -20, -5).getBlock();
        Block box = piston.getRelative(BlockFace.EAST);
        Block power = piston.getRelative(BlockFace.WEST);
        for (Block b : java.util.List.of(power, piston, box, box.getRelative(BlockFace.EAST))) {
            b.setType(Material.AIR, false);
            b.getRelative(BlockFace.DOWN).setType(Material.BARRIER, false);
        }
        removeItemsNear(box, 3);
        java.util.Set<java.util.UUID> oldItems = itemIds(box, 16);
        Sticker side = new Sticker(ICON.iconId(), ICON.iconVisual(), Anchor.SIDE, 0, Optional.of(Face.SOUTH));
        placeDecoratedBox(box, BlockFace.UP, side);
        // §9 "Box moved while the editor is open": an edit is open on this box when the piston fires.
        java.util.UUID holder = java.util.UUID.randomUUID();
        AtomicBoolean gone = editor.startSelfTestEdit(holder, box);
        Directional data = (Directional) Material.PISTON.createBlockData();
        data.setFacing(BlockFace.EAST);
        piston.setBlockData(data, false);
        power.setType(Material.REDSTONE_BLOCK, true);
        Bukkit.getRegionScheduler().runDelayed(plugin, origin, t -> {
            java.util.List<ItemStack> drops = shulkerDropsNear(box, 3, oldItems);
            plugin.getLogger().info("SELFTEST INFO piston: piston=" + piston.getBlockData().getAsString()
                    + " box spot=" + box.getType() + " drops=" + drops.stream().map(this::describe).toList());
            check("PRD 6.2: a box pushed by a piston drops an item that carries the sticker",
                    drops.size() == 1 && StickerStore.read(drops.getFirst().getPersistentDataContainer()).equals(Optional.of(side))
                            && box.getType() != Material.SHULKER_BOX);
            removeItemsNear(box, 3);
            power.setType(Material.AIR, false);
            pistonDuringEditCheck(box, piston, holder, gone);
            explosionTest(origin);
        }, 6);
    }

    /**
     * The editor saw the piston take the box, so a Save refuses with "moved or broken" even after
     * a box of the same color is put back at the same spot (PRD §6.3 step 1, §9).
     */
    private void pistonDuringEditCheck(Block box, Block piston, java.util.UUID holder, AtomicBoolean gone) {
        boolean seen = gone.get();
        piston.setType(Material.AIR, false);
        box.setType(Material.AIR, false); // the piston head
        Directional upright = (Directional) Material.SHULKER_BOX.createBlockData();
        upright.setFacing(BlockFace.UP);
        box.setBlockData(upright, false);
        io.github.jekitchen.shulkerstickers.core.BoxState original = new io.github.jekitchen.shulkerstickers.core.BoxState(
                BoxColor.UNDYED, Optional.empty(), Optional.empty(), Optional.empty());
        EditorService.SaveResult result = editor.apply(box, new EditorService.SaveRequest(original,
                original.withName(Optional.of("Moved")), new io.github.jekitchen.shulkerstickers.core.Changes(false, false, true),
                BlockFace.UP, 0, false, gone));
        boolean untouched = ((ShulkerBox) box.getState()).customName() == null;
        editor.endSelfTestEdit(holder);
        check("PRD 9: a box pushed by a piston during an edit makes Save refuse, even with a new box at the spot"
                        + " (piston seen " + seen + ", saved " + result.saved() + ", message " + result.messageKey()
                        + ", new box untouched " + untouched + ", lock released " + editor.editorOf(box).isEmpty() + ")",
                seen && !result.saved() && TextKeys.SAVE_BOX_GONE.equals(result.messageKey()) && untouched
                        && editor.editorOf(box).isEmpty());
        box.setType(Material.AIR, false);
    }

    /** §6.2 "Explosion": the box is destroyed and its drop carries the sticker and the contents. */
    private void explosionTest(Location origin) {
        Block box = origin.clone().add(4, -20, 4).getBlock();
        box.getRelative(BlockFace.DOWN).setType(Material.BARRIER, false);
        removeItemsNear(box, 3);
        java.util.Set<java.util.UUID> oldItems = itemIds(box, 16);
        placeDecoratedBox(box, BlockFace.UP, ICON);
        boolean exploded = box.getWorld().createExplosion(box.getLocation().add(0.5, 0.5, 0.5), 1f, false, true);
        Bukkit.getRegionScheduler().runDelayed(plugin, origin, t -> {
            java.util.List<ItemStack> drops = shulkerDropsNear(box, 3, oldItems);
            plugin.getLogger().info("SELFTEST INFO explosion: exploded=" + exploded + " box spot=" + box.getType()
                    + " drops=" + drops.stream().map(this::describe).toList());
            check("PRD 6.2: a box destroyed by an explosion drops an item that carries the sticker and contents",
                    drops.size() == 1 && StickerStore.read(drops.getFirst().getPersistentDataContainer()).equals(Optional.of(ICON))
                            && drops.getFirst().getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta meta
                            && meta.getBlockState() instanceof ShulkerBox inside
                            && inside.getInventory().getItem(26) != null
                            && inside.getInventory().getItem(26).getAmount() == 27);
            removeItemsNear(box, 3);
            itemTransferTests(origin);
        }, 4);
    }

    /** §6.2 "Hopper, dropper": a stickered box item keeps its sticker when a hopper moves it or a dropper drops it. */
    private void itemTransferTests(Location origin) {
        Block hopper = origin.clone().add(-5, -25, -5).getBlock();
        Block chest = hopper.getRelative(BlockFace.EAST);
        Block dropper = origin.clone().add(-5, -25, 1).getBlock();
        Block out = dropper.getRelative(BlockFace.EAST);
        for (Block b : java.util.List.of(hopper, chest, dropper, out)) {
            b.setType(Material.AIR, false);
        }
        // A barrier floor and a back wall keep the dropped item from sliding or falling away.
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block cell = out.getRelative(dx, 0, dz);
                cell.getRelative(BlockFace.DOWN).setType(Material.BARRIER, false);
                cell.setType(dx == 2 ? Material.BARRIER : Material.AIR, false);
            }
        }
        removeItemsNear(out, 4);
        // The explosion check above drops items that fall onto this spot; a solid block over the
        // hopper stops it collecting them (it then only takes what it's given here).
        removeItemsNear(hopper, 6);
        hopper.getRelative(BlockFace.UP).setType(Material.BARRIER, false);
        java.util.Set<java.util.UUID> oldItems = itemIds(out, 16);
        Sticker side = new Sticker(ICON.iconId(), ICON.iconVisual(), Anchor.SIDE, 0, Optional.of(Face.EAST));
        ItemStack stack = new ItemStack(Material.LIME_SHULKER_BOX);
        stack.editPersistentDataContainer(pdc -> StickerStore.write(pdc, side));

        chest.setType(Material.CHEST, false);
        placeHopper(hopper, BlockFace.EAST, stack.clone());
        Directional data = (Directional) Material.DROPPER.createBlockData();
        data.setFacing(BlockFace.EAST);
        dropper.setBlockData(data, false);
        org.bukkit.block.Dropper dropperState = (org.bukkit.block.Dropper) dropper.getState();
        dropperState.getSnapshotInventory().clear();
        dropperState.getSnapshotInventory().addItem(stack.clone());
        dropperState.update(true, false);
        ((org.bukkit.block.Dropper) dropper.getState()).drop();
        int leftInDropper = total(dropper);

        Bukkit.getRegionScheduler().runDelayed(plugin, origin, t -> {
            ItemStack moved = ((org.bukkit.block.Chest) chest.getState()).getInventory().getItem(0);
            java.util.List<String> stillInHopper = java.util.Arrays.stream(
                            ((org.bukkit.block.Hopper) hopper.getState()).getInventory().getContents())
                    .filter(java.util.Objects::nonNull).map(this::describe).toList();
            check("PRD 6.2: a stickered box item moved by a hopper keeps its sticker (" + describe(moved)
                            + "; still in the hopper: " + stillInHopper + ")",
                    moved != null && moved.getType() == Material.LIME_SHULKER_BOX
                            && StickerStore.read(moved.getPersistentDataContainer()).equals(Optional.of(side))
                            && total(hopper) == 0);
            java.util.List<ItemStack> dropped = shulkerDropsNear(out, 4, oldItems);
            plugin.getLogger().info("SELFTEST INFO dropper: items near the dropper: " + itemsNear(out, 4).stream()
                    .map(i -> i.getItemStack().getType() + " at " + i.getLocation().toVector() + " age " + i.getTicksLived())
                    .toList());
            check("PRD 6.2: a stickered box item dropped by a dropper keeps its sticker (left in dropper: " + leftInDropper + "; "
                            + dropped.stream().map(this::describe).toList() + ")",
                    dropped.size() == 1 && StickerStore.read(dropped.getFirst().getPersistentDataContainer()).equals(Optional.of(side)));
            removeItemsNear(out, 4);
            unknownIconTest(origin);
            boxInUseTest(origin, () -> cloneTest(origin));
        }, 12);
    }

    // ---- PRD §9 edge cases through the editor's Save (no player: Save's apply step is called directly) ----

    /**
     * §6.1, §9 "Icon item no longer exists": a valid v1 sticker whose icon is from a mod that is
     * gone draws no icon (the name still shows), and its raw data survives a rename and a recolor
     * through Save, and a break.
     */
    private void unknownIconTest(Location origin) {
        Block box = origin.clone().add(-4, -5, 0).getBlock();
        java.util.Map<String, Object> raw = java.util.Map.of(
                io.github.jekitchen.shulkerstickers.core.data.StickerFormat.V, 1,
                io.github.jekitchen.shulkerstickers.core.data.StickerFormat.ICON_ID, "nomod:thing",
                io.github.jekitchen.shulkerstickers.core.data.StickerFormat.ANCHOR, "lid",
                io.github.jekitchen.shulkerstickers.core.data.StickerFormat.ROT, 0);
        Directional upright = (Directional) Material.SHULKER_BOX.createBlockData();
        upright.setFacing(BlockFace.UP);
        box.setType(Material.AIR, false);
        box.setBlockData(upright, false);
        ShulkerBox state = (ShulkerBox) box.getState();
        state.customName(Component.text("Ores"));
        raw.forEach((k, v) -> {
            if (v instanceof Integer i) {
                state.getPersistentDataContainer().set(PaperKeys.sticker(k), org.bukkit.persistence.PersistentDataType.INTEGER, i);
            } else {
                state.getPersistentDataContainer().set(PaperKeys.sticker(k), org.bukkit.persistence.PersistentDataType.STRING, (String) v);
            }
        });
        state.update(true, false);
        Optional<Sticker> read = StickerStore.read(((ShulkerBox) box.getState()).getPersistentDataContainer());

        displays.forceNearby(true);
        displays.refresh(box);
        var shown = displays.displaysAt(box);
        check("PRD 9: an icon from a mod that is gone (nomod:thing, a valid v1 sticker) draws nothing; the name still does ("
                        + "read " + read + ", drawn " + shown.stream().map(e -> e.getType().name()).toList() + ")",
                read.flatMap(Sticker::iconId).equals(Optional.of("nomod:thing"))
                        && shown.size() == 1 && shown.getFirst() instanceof TextDisplay);

        io.github.jekitchen.shulkerstickers.core.BoxState original = new io.github.jekitchen.shulkerstickers.core.BoxState(
                BoxColor.UNDYED, Optional.of("nomod:thing"), Optional.empty(), Optional.of("Ores"));
        EditorService.SaveResult result = editor.apply(box, new EditorService.SaveRequest(original,
                original.withColor(BoxColor.CYAN).withName(Optional.of("Chests")),
                new io.github.jekitchen.shulkerstickers.core.Changes(true, false, true),
                BlockFace.UP, 0, false, new AtomicBoolean()));
        ShulkerBox after = (ShulkerBox) box.getState();
        java.util.Map<String, Object> rawAfter = StickerStore.raw(after.getPersistentDataContainer());
        check("PRD 9: its raw sticker data survives a rename and a recolor through Save (saved " + result.saved()
                        + ", now " + box.getType() + " named " + after.customName() + ", data " + rawAfter + ")",
                result.saved() && box.getType() == Material.CYAN_SHULKER_BOX
                        && Component.text("Chests").equals(after.customName()) && raw.equals(rawAfter));
        ItemStack drop = box.getDrops().stream().filter(i -> Tag.SHULKER_BOXES.isTagged(i.getType())).findFirst().orElse(null);
        java.util.Map<String, Object> dropped = drop == null ? java.util.Map.of() : StickerStore.raw(drop.getPersistentDataContainer());
        check("PRD 9: ...and a break drops it unchanged (" + dropped + ")", raw.equals(dropped));
        displays.forget(BlockKey.of(box));
        box.setType(Material.AIR, false);
    }

    /**
     * §5.2, §5.5, §9 "Box open at Save": with someone viewing the box, a Save of color, icon and name
     * applies the icon and name, refuses the color with "Box is in use", and the displays stay hidden
     * until the viewer closes the box. Nobody is online, so a stand-in viewer is put in the box's
     * viewer list (what the server checks) and DisplayListener gets the open and close events.
     */
    private void boxInUseTest(Location origin, Runnable then) {
        Block box = origin.clone().add(-2, -5, 0).getBlock();
        // On a side face: side displays hide while the box is open (lid-face ones move with the
        // lid instead, see lidFollowTest).
        Sticker side = new Sticker(ICON.iconId(), ICON.iconVisual(), Anchor.SIDE, 0, Optional.of(Face.NORTH));
        placeDecoratedBox(box, BlockFace.UP, side);
        ShulkerBox unlocked = (ShulkerBox) box.getState();
        unlocked.setLockItem(null); // Save checks the lock; this test is about the viewer
        unlocked.update(true, false);
        displays.forceNearby(true);
        displays.refresh(box);
        int drawnBefore = displays.displaysAt(box).size();

        org.bukkit.inventory.Inventory inventory = ((ShulkerBox) box.getState(false)).getInventory();
        org.bukkit.entity.HumanEntity viewer = stub(org.bukkit.entity.HumanEntity.class, java.util.Map.of());
        org.bukkit.inventory.InventoryView view = stub(org.bukkit.inventory.InventoryView.class,
                java.util.Map.of("getTopInventory", inventory, "getPlayer", viewer));
        DisplayListener listener = new DisplayListener(plugin, settings, displays);
        String setup;
        try {
            inventory.getViewers().add(viewer);
            setup = "viewers " + inventory.getViewers().size();
        } catch (RuntimeException e) {
            setup = "can't add a viewer: " + e;
        }
        String setupNote = setup;
        listener.onOpen(new org.bukkit.event.inventory.InventoryOpenEvent(view));
        Bukkit.getRegionScheduler().runDelayed(plugin, origin, t1 -> {
            boolean hiddenOnOpen = displays.displaysAt(box).isEmpty();
            io.github.jekitchen.shulkerstickers.core.BoxState original = new io.github.jekitchen.shulkerstickers.core.BoxState(
                    BoxColor.UNDYED, ICON.iconId(), ICON.iconVisual(), Optional.of("Ores"));
            EditorService.SaveResult result = editor.apply(box, new EditorService.SaveRequest(original,
                    original.withColor(BoxColor.RED).withIcon(Optional.of("minecraft:diamond"), Optional.empty())
                            .withName(Optional.of("Gems")),
                    new io.github.jekitchen.shulkerstickers.core.Changes(true, true, true),
                    BlockFace.NORTH, 0, false, new AtomicBoolean()));
            ShulkerBox after = (ShulkerBox) box.getState();
            boolean hiddenAfterSave = displays.displaysAt(box).isEmpty();
            check("PRD 9: Save while someone has the box open applies icon and name, refuses only the color (box in use)"
                            + " (" + setupNote + "; saved " + result.saved() + ", message " + result.messageKey() + ", applied "
                            + result.applied() + ", now " + box.getType() + " named " + after.customName() + ", sticker "
                            + StickerStore.read(after.getPersistentDataContainer()) + ")",
                    result.saved() && TextKeys.SAVE_BOX_IN_USE.equals(result.messageKey())
                            && result.applied().equals(new io.github.jekitchen.shulkerstickers.core.Changes(false, true, true))
                            && box.getType() == Material.SHULKER_BOX
                            && Component.text("Gems").equals(after.customName())
                            && StickerStore.read(after.getPersistentDataContainer()).flatMap(Sticker::iconId)
                                    .equals(Optional.of("minecraft:diamond")));
            check("PRD 5.5: side displays are hidden while the box is open, also after that Save (drawn before " + drawnBefore
                            + ", hidden on open " + hiddenOnOpen + ", after Save " + hiddenAfterSave + ")",
                    drawnBefore == 2 && hiddenOnOpen && hiddenAfterSave);
            try {
                inventory.getViewers().remove(viewer);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("SELFTEST can't remove the stand-in viewer: " + e);
            }
            listener.onClose(new org.bukkit.event.inventory.InventoryCloseEvent(view));
            Bukkit.getRegionScheduler().runDelayed(plugin, origin, t2 -> {
                var shown = displays.displaysAt(box);
                boolean newIcon = shown.stream().anyMatch(e -> e instanceof ItemDisplay d
                        && d.getItemStack().getType() == Material.DIAMOND);
                check("PRD 5.5: the displays come back, with the new icon, once the viewer closes the box ("
                                + shown.stream().map(e -> e.getType() + (e.isValid() ? "" : "(invalid)")).toList() + ")",
                        shown.size() == 2 && shown.stream().allMatch(Entity::isValid) && newIcon);
                displays.forget(BlockKey.of(box));
                box.setType(Material.AIR, false);
                lidFollowTest(origin, then);
            }, 30);
        }, 3);
    }

    /**
     * PRD §5.5 (v0.2): displays on the lid face stay drawn while the box is open and move with the
     * lid: fully open, each one's transformation is the shared lid motion applied to its rest pose
     * (vanilla: 0.5 blocks up along the facing, -270 degrees about it). After the box closes they
     * are back at rest. Opened and closed through DisplayListener with a stand-in viewer.
     */
    private void lidFollowTest(Location origin, Runnable then) {
        Block box = origin.clone().add(-2, -5, 3).getBlock();
        placeDecoratedBox(box, BlockFace.EAST, ICON); // icon and name on the lid, facing east
        displays.forceNearby(true);
        displays.refresh(box);
        BlockKey key = BlockKey.of(box);
        java.util.List<Entity> drawn = displays.displaysAt(box);
        java.util.Map<Entity, org.bukkit.util.Transformation> rest = new java.util.HashMap<>();
        drawn.forEach(e -> rest.put(e, ((Display) e).getTransformation()));
        boolean onLid = displays.onLid(key);

        org.bukkit.inventory.Inventory inventory = ((ShulkerBox) box.getState(false)).getInventory();
        org.bukkit.entity.HumanEntity viewer = stub(org.bukkit.entity.HumanEntity.class, java.util.Map.of());
        org.bukkit.inventory.InventoryView view = stub(org.bukkit.inventory.InventoryView.class,
                java.util.Map.of("getTopInventory", inventory, "getPlayer", viewer));
        DisplayListener listener = new DisplayListener(plugin, settings, displays);
        inventory.getViewers().add(viewer);
        listener.onOpen(new org.bukkit.event.inventory.InventoryOpenEvent(view));
        Bukkit.getRegionScheduler().runDelayed(plugin, origin, t1 -> {
            java.util.List<Entity> open = displays.displaysAt(box);
            org.joml.Vector3f facing = new org.joml.Vector3f(1, 0, 0);
            StringBuilder why = new StringBuilder();
            boolean posed = !open.isEmpty();
            for (Entity e : open) {
                org.bukkit.util.Transformation was = rest.get(e);
                org.bukkit.util.Transformation now = ((Display) e).getTransformation();
                if (was == null) {
                    posed = false;
                    why.append(" new entity ").append(e.getType());
                    continue;
                }
                org.joml.Vector3f standOffset = e.getLocation().toVector()
                        .subtract(box.getLocation().add(0.5, 0.5, 0.5).toVector()).toVector3f();
                var want = io.github.jekitchen.shulkerstickers.core.display.LidMotion.at(1f, facing, standOffset,
                        was.getTranslation(), was.getLeftRotation());
                boolean ok = want.translation().distance(now.getTranslation()) < 1.0E-4f
                        && want.leftRotation().equals(now.getLeftRotation(), 1.0E-4f);
                posed &= ok;
                if (!ok) {
                    why.append(' ').append(e.getType()).append(" at ").append(now.getTranslation())
                            .append(" want ").append(want.translation());
                }
            }
            check("PRD 5.5: lid-face displays stay drawn and move with the lid when the box opens (on lid " + onLid
                            + ", lid " + displays.lidTenths(key) + "/10, drawn " + drawn.size() + " then " + open.size()
                            + why + ")",
                    onLid && drawn.size() == 2 && open.size() == 2 && displays.lidTenths(key) == 10 && posed);
            inventory.getViewers().remove(viewer);
            listener.onClose(new org.bukkit.event.inventory.InventoryCloseEvent(view));
            Bukkit.getRegionScheduler().runDelayed(plugin, origin, t2 -> {
                java.util.List<Entity> after = displays.displaysAt(box);
                boolean atRest = after.size() == 2 && after.stream().allMatch(e -> {
                    org.bukkit.util.Transformation was = rest.get(e);
                    org.bukkit.util.Transformation now = ((Display) e).getTransformation();
                    return was != null && was.getTranslation().distance(now.getTranslation()) < 1.0E-4f
                            && was.getLeftRotation().equals(now.getLeftRotation(), 1.0E-4f);
                });
                check("PRD 5.5: lid-face displays are back at rest once the box closes (lid " + displays.lidTenths(key)
                                + "/10, drawn " + after.size() + ")",
                        atRest && displays.lidTenths(key) == 0);
                displays.forget(key);
                box.setType(Material.AIR, false);
                then.run();
            }, 15);
        }, 15);
    }

    /**
     * A stand-in for an interface nobody on this server can provide (no players): answers
     * {@code answers} by method name, and a default for everything else.
     */
    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, java.util.Map<String, Object> answers) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                new java.lang.reflect.InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                        String name = method.getName();
                        if (answers.containsKey(name)) {
                            return answers.get(name);
                        }
                        Class<?> r = method.getReturnType();
                        return switch (name) {
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "selftest " + type.getSimpleName();
                            case "getName" -> "selftest";
                            default -> !r.isPrimitive() ? null
                                    : r == boolean.class ? false
                                    : r == void.class ? null
                                    : r == float.class ? 0f
                                    : r == double.class ? 0d
                                    : r == long.class ? 0L
                                    : r == char.class ? (char) 0
                                    : r == byte.class ? (byte) 0
                                    : r == short.class ? (short) 0
                                    : (Object) 0;
                        };
                    }
                });
    }

    /**
     * §6.2 "/clone, structure blocks": the copy keeps the sticker, and the chunk-load pass that
     * re-attaches displays draws them on it. A structure is placed on every server; {@code /clone}
     * runs only on Paper, since Folia/Canvas won't dispatch a console command from a region thread.
     */
    private void cloneTest(Location origin) {
        Block source = origin.clone().add(4, -30, 4).getBlock();
        Block structureCopy = source.getRelative(0, 0, -3);
        Block commandCopy = source.getRelative(0, 0, -6);
        for (Block b : java.util.List.of(structureCopy, commandCopy)) {
            b.setType(Material.AIR, false);
        }
        placeDecoratedBox(source, BlockFace.UP, ICON);

        var structure = Bukkit.getStructureManager().createStructure();
        structure.fill(source.getLocation(), new org.bukkit.util.BlockVector(1, 1, 1), false);
        structure.place(structureCopy.getLocation(), false, org.bukkit.block.structure.StructureRotation.NONE,
                org.bukkit.block.structure.Mirror.NONE, 0, 1f, new java.util.Random(1));
        java.util.List<Block> copies = new java.util.ArrayList<>(java.util.List.of(structureCopy));
        if (DisplayManager.FOLIA) {
            // Folia/Canvas refuse a console command from a region thread ("Dispatching command async").
            plugin.getLogger().info("SELFTEST INFO /clone skipped: Folia/Canvas don't dispatch console commands"
                    + " from a region thread; the structure copy covers the same block-entity copy");
        } else {
            String command = "clone %d %d %d %d %d %d %d %d %d".formatted(source.getX(), source.getY(), source.getZ(),
                    source.getX(), source.getY(), source.getZ(), commandCopy.getX(), commandCopy.getY(), commandCopy.getZ());
            boolean ran = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            plugin.getLogger().info("SELFTEST INFO /" + command + " -> " + ran + ", copy is " + commandCopy.getType());
            copies.add(commandCopy);
        }
        for (Block copy : copies) {
            String how = copy == structureCopy ? "structure" : "/clone";
            check("PRD 6.2: a " + how + " copy of a decorated box keeps its sticker and name",
                    copy.getState() instanceof ShulkerBox box
                            && StickerStore.read(box.getPersistentDataContainer()).equals(Optional.of(ICON))
                            && Component.text("Ores").equals(box.customName()));
        }
        // Nothing announces a copy in a loaded chunk; displays come with the next chunk load. Run that pass.
        displays.forceNearby(true);
        new DisplayListener(plugin, settings, displays)
                .onChunkLoad(new org.bukkit.event.world.ChunkLoadEvent(source.getChunk(), false));
        Bukkit.getRegionScheduler().runDelayed(plugin, origin, t -> {
            for (Block copy : copies) {
                String how = copy == structureCopy ? "structure" : "/clone";
                var shown = displays.displaysAt(copy);
                check("PRD 6.2: the chunk-load pass draws a " + how + " copy's icon and name ("
                                + shown.stream().map(e -> e.getType().name()).toList() + ")",
                        shown.size() == 2 && shown.stream().allMatch(Entity::isValid));
            }
            displays.removeAll();
            uninstallWitness(origin);
            // A box in the origin chunk's last column, facing chunk (1, 0), which is held loaded.
            World world = origin.getWorld();
            world.getChunkAtAsync(1, 0).thenAccept(neighbour -> Bukkit.getRegionScheduler().execute(plugin, origin, () -> {
                neighbour.addPluginChunkTicket(plugin);
                try {
                    checkChunkEdge(world.getBlockAt(15, 250, 12), "next to a loaded chunk", true);
                } finally {
                    neighbour.removePluginChunkTicket(plugin);
                    chunkEdgeTest(world, () -> chunkReloadTest(world, this::summary));
                }
            }));
        }, 3);
    }

    /**
     * A box in a chunk's last column with its icon on the outward face. Runs at chunk (100, 100),
     * far from spawn, so the neighbour (101, 100) is normally not loaded: the displays must then
     * stand in the box's own chunk and not load or use the neighbour.
     */
    private void chunkEdgeTest(World world, Runnable then) {
        Location edge = new Location(world, 100 * 16 + 15, 250, 100 * 16 + 8);
        world.getChunkAtAsync(edge).thenAccept(chunk -> Bukkit.getRegionScheduler().execute(plugin, edge, () -> {
            chunk.addPluginChunkTicket(plugin);
            try {
                checkChunkEdge(edge.getBlock(), "next to an unloaded chunk", false);
            } finally {
                chunk.removePluginChunkTicket(plugin);
                then.run();
            }
        }));
    }

    /** Far from spawn and from every other check, so nothing else keeps it loaded. */
    private static final int RELOAD_CHUNK_X = -120;
    private static final int RELOAD_CHUNK_Z = 90;
    /** How long to wait for the chunk to unload once its ticket is gone (polled every 10 ticks). */
    private static final int UNLOAD_WAIT_TICKS = 1200;

    /**
     * §9 "Chunk unload/reload": a decorated box in a far chunk held by a plugin ticket gets its
     * displays; the ticket is released and the chunk really unloads; loading it again draws the
     * icon and name anew, and the old display entities are gone.
     */
    private void chunkReloadTest(World world, Runnable then) {
        Location at = new Location(world, RELOAD_CHUNK_X * 16 + 8, 250, RELOAD_CHUNK_Z * 16 + 8);
        world.getChunkAtAsync(at).thenAccept(chunk -> Bukkit.getRegionScheduler().execute(plugin, at, () -> {
            chunk.addPluginChunkTicket(plugin);
            java.util.List<Entity> before = java.util.List.of();
            String error = "";
            try {
                placeDecoratedBox(at.getBlock(), BlockFace.UP, ICON);
                displays.forceNearby(true);
                displays.refresh(at.getBlock());
                before = java.util.List.copyOf(displays.displaysAt(at.getBlock()));
            } catch (RuntimeException e) {
                error = "; error: " + e;
                plugin.getLogger().log(java.util.logging.Level.WARNING, "SELFTEST chunk reload", e);
            }
            check("chunk reload setup: a decorated box in chunk " + RELOAD_CHUNK_X + "," + RELOAD_CHUNK_Z
                            + " held by a plugin ticket draws its icon and name (" + before.size() + " displays" + error + ")",
                    before.size() == 2 && before.stream().allMatch(Entity::isValid));
            java.util.List<Entity> old = before;
            chunk.removePluginChunkTicket(plugin);
            awaitUnload(world, at, old, then);
        }));
    }

    /** Global region thread: polls until the chunk is unloaded (or the wait runs out), then loads it again. */
    private void awaitUnload(World world, Location at, java.util.List<Entity> old, Runnable then) {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, new java.util.function.Consumer<>() {
            int waited;

            @Override
            public void accept(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
                boolean loaded;
                try {
                    loaded = world.isChunkLoaded(RELOAD_CHUNK_X, RELOAD_CHUNK_Z);
                } catch (RuntimeException e) {
                    task.cancel();
                    check("chunk reload: can ask whether the chunk is loaded: " + e, false);
                    then.run();
                    return;
                }
                if (loaded && waited < UNLOAD_WAIT_TICKS) {
                    waited += 10;
                    return;
                }
                task.cancel();
                boolean forgotten = displays.trackedInChunk(world.getUID(), RELOAD_CHUNK_X, RELOAD_CHUNK_Z).isEmpty();
                check("chunk reload: the chunk unloads once its ticket is released, and its box is forgotten (unloaded "
                                + !loaded + " after about " + waited + " ticks, forgotten " + forgotten + ")",
                        !loaded && forgotten);
                reload(world, at, old, then);
            }
        }, 10, 10);
    }

    private void reload(World world, Location at, java.util.List<Entity> old, Runnable then) {
        displays.forceNearby(true);
        world.getChunkAtAsync(at).thenAccept(chunk -> Bukkit.getRegionScheduler().execute(plugin, at, () -> {
            chunk.addPluginChunkTicket(plugin);
            // The chunk-load pass draws the box on the chunk's region; give it time, checking every 10 ticks.
            Bukkit.getRegionScheduler().runAtFixedRate(plugin, at, new java.util.function.Consumer<>() {
                int left = 10;

                @Override
                public void accept(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
                    Block box = at.getBlock();
                    var shown = displays.displaysAt(box);
                    boolean drawn = shown.size() == 2 && shown.stream().allMatch(Entity::isValid);
                    if (!drawn && --left > 0) {
                        return;
                    }
                    task.cancel();
                    boolean oldGone = old.stream().noneMatch(Entity::isValid) && old.stream().noneMatch(shown::contains);
                    long ours = box.getWorld().getNearbyEntities(box.getLocation().add(0.5, 0.5, 0.5), 3, 3, 3,
                            e -> e instanceof Display && e.getPersistentDataContainer().has(PaperKeys.DISPLAY)).size();
                    check("chunk reload: after the chunk loads again the box's icon and name are drawn anew and the old"
                                    + " displays are gone (" + shown.stream().map(e -> e.getType() + (e.isValid() ? "" : "(invalid)"))
                                            .toList() + ", old gone " + oldGone + ", our displays near the box " + ours + ")",
                            drawn && oldGone && ours == 2);
                    try {
                        displays.forget(BlockKey.of(box));
                        box.setType(Material.AIR, false);
                    } finally {
                        chunk.removePluginChunkTicket(plugin);
                        then.run();
                    }
                }
            }, 10, 10);
        }));
    }

    /**
     * Places a box at {@code box} (a chunk's last x column) with its icon and name on the east
     * face, then checks: the drawing appears just in front of the face; the displays stand in the
     * next chunk if it is loaded, else in the box's own chunk; and the next chunk isn't loaded by it.
     */
    private void checkChunkEdge(Block box, String label, boolean neighbourShouldBeLoaded) {
        World world = box.getWorld();
        int ownChunk = box.getX() >> 4;
        int chunkZ = box.getZ() >> 4;
        boolean neighbourBefore = world.isChunkLoaded(ownChunk + 1, chunkZ);
        check("chunk-edge setup: the neighbouring chunk is " + (neighbourShouldBeLoaded ? "" : "not ") + "loaded",
                neighbourBefore == neighbourShouldBeLoaded);
        Sticker outward = new Sticker(ICON.iconId(), ICON.iconVisual(), Anchor.SIDE, 0, Optional.of(Face.EAST));
        String error = "";
        java.util.List<Entity> shown = java.util.List.of();
        try {
            placeDecoratedBox(box, BlockFace.UP, outward);
            displays.forceNearby(true);
            displays.refresh(box);
            shown = displays.displaysAt(box);
        } catch (RuntimeException e) {
            error = "; error: " + e;
            plugin.getLogger().log(java.util.logging.Level.WARNING, "SELFTEST chunk edge", e);
        }
        int expectedChunk = neighbourBefore ? ownChunk + 1 : ownChunk;
        boolean rightChunk = shown.stream().allMatch(e -> e.getLocation().getBlockX() >> 4 == expectedChunk
                && e.getLocation().getBlockZ() >> 4 == chunkZ);
        boolean neighbourAfter = world.isChunkLoaded(ownChunk + 1, chunkZ);
        // Where each display is drawn: its position plus its translation (world axes, yaw and pitch 0).
        double wantedX = box.getX() + 0.5 + io.github.jekitchen.shulkerstickers.core.display.DisplayLayout.FACE_OFFSET;
        java.util.List<Double> drawnX = shown.stream()
                .map(e -> e.getLocation().getX() + ((Display) e).getTransformation().getTranslation().x())
                .toList();
        boolean drawnInFront = drawnX.stream().allMatch(x -> Math.abs(x - wantedX) < 1.0E-3);
        check("a box on a chunk edge " + label + " draws its outward icon and name in front of the face, standing in chunk "
                        + expectedChunk + " (entities at " + shown.stream().map(e -> e.getLocation().toVector().toString()).toList()
                        + ", drawn at x " + drawnX + ", wanted " + wantedX
                        + "; neighbour loaded before " + neighbourBefore + ", after " + neighbourAfter + error + ")",
                error.isEmpty() && shown.size() == 2 && rightChunk && drawnInFront
                        && neighbourBefore == neighbourAfter);
        try {
            displays.remove(BlockKey.of(box));
        } finally {
            box.setType(Material.AIR, false);
        }
    }

    /**
     * Left in the world for {@code scripts/paper-uninstall.sh} (AC-6): a full, named, locked,
     * stickered box at 2 250 2 on a stone block, with its displays up when the server stops.
     * Same chunk as the origin, so the same region thread owns it on Folia and Canvas.
     */
    private void uninstallWitness(Location origin) {
        Block box = origin.clone().add(-6, 0, -6).getBlock();
        box.getRelative(BlockFace.DOWN).setType(Material.STONE, false);
        placeDecoratedBox(box, BlockFace.UP, ICON);
        displays.forceNearby(true);
        displays.refresh(box);
        check("uninstall witness at 2 250 2 has its icon and name drawn", displays.displaysAt(box).size() == 2);
    }

    /** Paper with claim plugins installed ({@code runClaims}). */
    private static boolean claimsRun() {
        return !System.getProperty("shulkerstickers.selftest.claims", "").isBlank();
    }

    /** An upright box with an icon sticker, a name and the given items. */
    private static void placeStickeredBox(Block block, ItemStack... items) {
        Directional data = (Directional) Material.SHULKER_BOX.createBlockData();
        data.setFacing(BlockFace.UP);
        block.setType(Material.AIR, false);
        block.setBlockData(data, false);
        ShulkerBox state = (ShulkerBox) block.getState();
        state.getSnapshotInventory().clear();
        for (int slot = 0; slot < items.length; slot++) {
            state.getSnapshotInventory().setItem(slot, items[slot]);
        }
        state.customName(Component.text("Ores"));
        StickerStore.write(state.getPersistentDataContainer(), ICON);
        state.update(true, false);
    }

    private static void placeHopper(Block block, BlockFace facing, ItemStack... items) {
        org.bukkit.block.data.type.Hopper data = (org.bukkit.block.data.type.Hopper) Material.HOPPER.createBlockData();
        data.setFacing(facing);
        block.setType(Material.AIR, false);
        block.setBlockData(data, false);
        org.bukkit.block.Hopper state = (org.bukkit.block.Hopper) block.getState();
        state.getSnapshotInventory().clear();
        for (int slot = 0; slot < items.length; slot++) {
            state.getSnapshotInventory().setItem(slot, items[slot]);
        }
        state.update(true, false);
    }

    /** Item counts by type across the containers. */
    private static java.util.Map<Material, Integer> count(Block... containers) {
        java.util.Map<Material, Integer> counts = new java.util.TreeMap<>();
        for (Block block : containers) {
            if (block.getState(false) instanceof org.bukkit.block.Container container) {
                for (ItemStack stack : container.getInventory().getContents()) {
                    if (stack != null && !stack.isEmpty()) {
                        counts.merge(stack.getType(), stack.getAmount(), Integer::sum);
                    }
                }
            }
        }
        return counts;
    }

    /** "+N item" for each duplicated item, "-N item" for each lost one. */
    private static String difference(java.util.Map<Material, Integer> expected, java.util.Map<Material, Integer> actual) {
        java.util.Set<Material> types = new java.util.TreeSet<>(expected.keySet());
        types.addAll(actual.keySet());
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Material type : types) {
            int delta = actual.getOrDefault(type, 0) - expected.getOrDefault(type, 0);
            if (delta != 0) {
                out.add((delta > 0 ? "+" : "") + delta + " " + type);
            }
        }
        return out.isEmpty() ? "no difference" : String.join(", ", out);
    }

    private static int total(Block container) {
        return count(container).values().stream().mapToInt(Integer::intValue).sum();
    }

    private static java.util.List<Item> itemsNear(Block block, double radius) {
        return block.getWorld().getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), radius, radius, radius,
                e -> e instanceof Item).stream().map(e -> (Item) e).toList();
    }

    /** Item entities near the block that weren't there when {@code before} was taken. */
    private static java.util.List<Item> newItemsNear(Block block, double radius, java.util.Set<java.util.UUID> before) {
        return itemsNear(block, radius).stream().filter(i -> !before.contains(i.getUniqueId())).toList();
    }

    private static java.util.Set<java.util.UUID> itemIds(Block block, double radius) {
        return itemsNear(block, radius).stream().map(Entity::getUniqueId).collect(java.util.stream.Collectors.toSet());
    }

    /** Shulker box items dropped near the block since {@code before} was taken (leftovers from an earlier run don't count). */
    private static java.util.List<ItemStack> shulkerDropsNear(Block block, double radius, java.util.Set<java.util.UUID> before) {
        return newItemsNear(block, radius, before).stream().map(Item::getItemStack)
                .filter(i -> Tag.SHULKER_BOXES.isTagged(i.getType())).toList();
    }

    private static void removeItemsNear(Block block, double radius) {
        itemsNear(block, radius).forEach(Entity::remove);
    }

    private String describe(ItemStack stack) {
        return stack == null ? "nothing" : stack.getType() + " sticker=" + StickerStore.read(stack.getPersistentDataContainer());
    }

    /** Owner decision 2: the server's own lock check, reached by reflection, accepts the key. */
    private void lockKeyTests(Block box) {
        ItemStack key = new ItemStack(Material.STICK);
        key.editMeta(meta -> meta.customName(Component.text("key")));
        ItemStack wrong = new ItemStack(Material.STICK);
        wrong.editMeta(meta -> meta.customName(Component.text("not the key")));
        Block locked = box.getRelative(0, 0, 4);
        locked.setType(Material.SHULKER_BOX, false);
        ShulkerBox lockedState = (ShulkerBox) locked.getState();
        lockedState.setLockItem(key);
        lockedState.update(true, false);
        var state = locked.getState(false);
        check("a locked box accepts its key (server lock check by reflection)",
                LockKeys.unlocksWith(state, key, plugin.getLogger()));
        check("a locked box refuses the wrong key and an empty hand",
                !LockKeys.unlocksWith(state, wrong, plugin.getLogger())
                        && !LockKeys.unlocksWith(state, new ItemStack(Material.AIR), plugin.getLogger()));
        locked.setType(Material.AIR, false);
    }

    /** Owner decision 3, with real plugins installed ({@code :paper:runClaims -Pselftest}). */
    private void claimTests(Block box) {
        String expected = System.getProperty("shulkerstickers.selftest.claims", "");
        if (expected.isBlank()) {
            return;
        }
        var claims = io.github.jekitchen.shulkerstickers.paper.claims.Claims.detect(plugin);
        java.util.List<String> want = java.util.List.of(expected.split(","));
        check("claim plugins detected: " + claims.plugins(), claims.plugins().containsAll(want));
        java.util.List<String> failed = claims.probeFailures(box);
        check("claim hooks call the installed plugins' APIs" + (failed.isEmpty() ? "" : ": " + failed), failed.isEmpty());
    }

    /** Owner decision 4: a box saved by Fabric (sticker in custom_data only) is adopted on Paper. */
    private void migrationTests(Block box) {
        Block fabricBox = box.getRelative(0, 0, 6);
        fabricBox.setType(Material.AIR, false);
        fabricBox.setType(Material.SHULKER_BOX, false);
        java.util.Map<String, Object> fabricData = new java.util.LinkedHashMap<>();
        fabricData.put("v", 1);
        fabricData.put("icon_id", "minecraft:diamond");
        fabricData.put("anchor", "lid");
        fabricData.put("rot", 0);
        try {
            WorldMigration.putFabricStyle(fabricBox, fabricData);
        } catch (ReflectiveOperationException e) {
            check("set up a Fabric-style box: " + e, false);
            return;
        }
        boolean adopted = WorldMigration.migrate(fabricBox, plugin.getLogger());
        Optional<Sticker> read = StickerStore.read(((ShulkerBox) fabricBox.getState()).getPersistentDataContainer());
        check("a Fabric-saved box's sticker moves into persistent data",
                adopted && read.flatMap(Sticker::iconId).equals(Optional.of("minecraft:diamond")));
        check("our keys are removed from custom_data afterwards (a second pass finds nothing)",
                !WorldMigration.migrate(fabricBox, plugin.getLogger()));

        // A Paper box placed from a stickered item: persistent data wins over the stale item copy.
        placeDecoratedBox(fabricBox, BlockFace.UP, ICON);
        try {
            WorldMigration.putFabricStyle(fabricBox, fabricData);
        } catch (ReflectiveOperationException e) {
            check("set up a stale custom_data copy: " + e, false);
            return;
        }
        boolean replaced = WorldMigration.migrate(fabricBox, plugin.getLogger());
        Optional<Sticker> kept = StickerStore.read(((ShulkerBox) fabricBox.getState()).getPersistentDataContainer());
        check("persistent data is kept over a stale custom_data copy", !replaced && kept.equals(Optional.of(ICON)));
        fabricBox.setType(Material.AIR, false);
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
