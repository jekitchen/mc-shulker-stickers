package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.display.DisplayLayout;
import io.github.jekitchen.shulkerstickers.core.display.LidMotion;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.core.rules.NameRules;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a box's icon and name with real display entities that are never saved (PRD §7.2).
 *
 * <p>Displays exist only while a player is within view range (AC-8): 2,000 idle display
 * entities cost about 0.6 ms/tick even with nobody watching. Every loaded box with sticker
 * data or a name is tracked; once a second, a task on each such chunk's region spawns or
 * removes displays as players come and go.
 *
 * <p>Every method that touches the world must run on the thread that owns the box's region;
 * callers schedule through {@link #refreshLater}.
 */
class DisplayManager {

    /** Where players are around a block; region-thread safe on Folia (looks only at nearby entities). */
    @FunctionalInterface
    interface Nearby {
        /** Players within {@code horizontalRadius} across and {@code verticalRadius} up or down of the block centre. */
        List<Vector> players(Block anchor, double horizontalRadius, double verticalRadius);
    }

    static final Nearby PLAYERS = (anchor, radius, vertical) -> anchor.getLocation().add(0.5, 0.5, 0.5)
            .getNearbyPlayers(radius, vertical, radius).stream()
            .map(p -> p.getLocation().toVector())
            .toList();

    private static final long CHECK_TICKS = 20;

    /** What a box has to show, if a player is close enough. */
    record Parts(boolean icon, boolean name) {}

    /**
     * What is drawn now. {@code hiddenOpen}: hidden because the box is open (shown again on close).
     * {@code outside}: the displays stand in a neighbouring chunk (see {@link #spawnSpot}) and can
     * be lost with it. {@code lid}: set when the displays are on the lid face and move with it.
     */
    private record Drawn(List<Entity> entities, boolean icon, boolean name, boolean hiddenOpen, boolean outside,
            LidDisplays lid) {
        Drawn(List<Entity> entities, boolean icon, boolean name, boolean hiddenOpen, boolean outside) {
            this(entities, icon, name, hiddenOpen, outside, null);
        }
    }

    /** A lid-face display and its transformation with the box closed. */
    private record Rest(Display display, Transformation transformation) {}

    /** Displays on a box's lid face: they move with the lid (PRD §5.5, v0.2). */
    private record LidDisplays(Vector3f facing, Vector3f standOffset, List<Rest> rests) {}

    /**
     * How far a box's lid is open, in tenths, while it moves or stays open. Paper has no API for
     * the lid's progress, but vanilla moves it a fixed 0.1 per tick from the first viewer opening
     * the box and back from the last one closing it, so this follows the same steps.
     */
    private static final class LidState {
        int tenths;
        boolean opening;
        ScheduledTask task;
    }

    /** Boxes whose lid is moving or open; touched on the box's region thread only. */
    private final Map<BlockKey, LidState> lids = new ConcurrentHashMap<>();

    private record ChunkKey(UUID world, int x, int z) {
        static ChunkKey of(BlockKey key) {
            return new ChunkKey(key.world(), key.chunkX(), key.chunkZ());
        }
    }

    private final Plugin plugin;
    private final Settings settings;
    private final Nearby nearby;
    private final Map<BlockKey, Drawn> displays = new ConcurrentHashMap<>();
    private final Map<BlockKey, Parts> tracked = new ConcurrentHashMap<>();
    private final Map<ChunkKey, ScheduledTask> checks = new ConcurrentHashMap<>();
    /** {@link #tracked}, indexed by chunk, so each chunk's check only looks at its own boxes. */
    private final Map<ChunkKey, java.util.Set<BlockKey>> byChunk = new ConcurrentHashMap<>();
    /** Self-test and perf-test hook: treat every box as having a player nearby. */
    private volatile boolean forceNearby;

    DisplayManager(Plugin plugin, Settings settings) {
        this(plugin, settings, PLAYERS);
    }

    DisplayManager(Plugin plugin, Settings settings, Nearby nearby) {
        this.plugin = plugin;
        this.settings = settings;
        this.nearby = nearby;
    }

    void forceNearby(boolean force) {
        forceNearby = force;
    }

    /** Schedules {@link #refresh} on the box's region thread. */
    void refreshLater(Block block) {
        plugin.getServer().getRegionScheduler().execute(plugin, block.getLocation(), () -> refreshIfLoaded(block));
    }

    /** Region thread. Refreshes only if the box's chunk is still loaded; otherwise forgets it. */
    private void refreshIfLoaded(Block block) {
        if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
            forget(BlockKey.of(block));
            return;
        }
        refresh(block);
    }

    /**
     * Schedules one delayed refresh per box: while one is waiting, more requests for the same box
     * are dropped (a player opening and closing a box quickly would otherwise queue a redraw, and
     * respawned displays, for every close).
     */
    /** @return whether a refresh was scheduled (false: one is already waiting for this box) */
    boolean refreshLater(BlockKey key, long delayTicks) {
        Location location = key.location();
        if (location == null) {
            return false;
        }
        long ticks = Math.max(1, delayTicks);
        long now = System.nanoTime();
        // The mark expires a few seconds after the refresh was due, so a refresh that never ran
        // (its region unloaded first) can't block this box's refreshes for good.
        long expires = now + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(ticks * 50 + 5_000);
        boolean[] scheduled = {false};
        delayedRefreshes.compute(key, (k, until) -> {
            if (until != null && now < until) {
                return until;
            }
            scheduled[0] = true;
            return expires;
        });
        if (!scheduled[0]) {
            return false;
        }
        plugin.getServer().getRegionScheduler().runDelayed(plugin, location, t -> {
            delayedRefreshes.remove(key, expires);
            refreshIfLoaded(location.getBlock());
        }, ticks);
        return true;
    }

    /** Boxes with a delayed refresh waiting, and when that wait counts as stale (see {@link #refreshLater(BlockKey, long)}). */
    private final Map<BlockKey, Long> delayedRefreshes = new ConcurrentHashMap<>();

    void removeLater(Block block) {
        plugin.getServer().getRegionScheduler().execute(plugin, block.getLocation(), () -> forget(BlockKey.of(block)));
    }

    /** Redraws every loaded box with sticker data or a name (after a config reload). */
    void refreshAllLater() {
        java.util.Set<BlockKey> keys = new java.util.HashSet<>(tracked.keySet());
        keys.addAll(displays.keySet());
        keys.forEach(key -> refreshLater(key, 1));
    }

    boolean isTracked(Block block) {
        return tracked.containsKey(BlockKey.of(block));
    }

    /** Region thread only. Re-reads the box and draws what is in range of a player. */
    void refresh(Block block) {
        BlockKey key = BlockKey.of(block);
        removeEntities(key);
        if (!Tag.SHULKER_BOXES.isTagged(block.getType()) || !(block.getState(false) instanceof ShulkerBox box)) {
            untrack(key);
            return;
        }
        var pdc = box.getPersistentDataContainer();
        if (!StickerStore.hasData(pdc) && box.customName() == null) {
            untrack(key);
            return;
        }
        StickerConfig config = settings.config();
        Optional<Sticker> sticker = StickerStore.read(pdc);
        Optional<ItemStack> icon = config.iconEnabled()
                ? sticker.flatMap(s -> IconItems.render(s, config))
                : Optional.empty();
        Optional<String> name = Optional.ofNullable(box.customName())
                .filter(n -> config.nameDisplayEnabled())
                .filter(n -> !StickerStore.isNameHidden(pdc))
                .map(SafeNames::plain)
                .filter(n -> !n.isBlank());
        track(key, new Parts(icon.isPresent(), name.isPresent()));
        ensureCheck(key);
        BlockFace facing = ((Directional) block.getBlockData()).getFacing();
        // A name without a stored anchor (e.g. from a real anvil) goes on the lid.
        Anchor anchor = sticker.map(Sticker::anchor).orElse(Anchor.LID);
        BlockFace face = Faces.iconFace(anchor, facing, sticker.flatMap(Sticker::face));
        // Displays on the lid face move with the lid; the others are hidden while the box is open.
        boolean onLid = face == facing;
        if (!onLid && config.hideWhileOpen() && !box.getInventory().getViewers().isEmpty()) {
            displays.put(key, new Drawn(List.of(), false, false, true, false));
            return; // shown again when the last viewer closes the box
        }
        List<Vector> players = playersAround(block, config);
        boolean drawIcon = icon.isPresent() && inRange(block, players, config.iconViewRange());
        boolean drawName = name.isPresent() && inRange(block, players, config.nameViewRange());
        if (!drawIcon && !drawName) {
            return;
        }
        Quaternionf rotation = IconOrientation.rotation(Faces.toCore(face), sticker.map(Sticker::rot).orElse(0));
        Location wanted = block.getLocation().add(0.5, 0.5, 0.5).add(face.getDirection().multiply(DisplayLayout.FACE_OFFSET));
        Location at = spawnSpot(wanted, block);
        Vector3f shift = wanted.toVector().subtract(at.toVector()).toVector3f();

        List<Entity> spawned = new ArrayList<>(2);
        if (drawIcon) {
            float up = DisplayLayout.iconOffset(name.isPresent());
            spawned.add(block.getWorld().spawn(at, ItemDisplay.class, display -> {
                prepare(display, config);
                display.setItemStack(icon.get());
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                display.setViewRange(viewRange(config.iconViewRange()));
                display.setTransformation(transform(rotation, up, DisplayLayout.ICON_SCALE, shift));
            }));
        }
        if (drawName) {
            String text = NameRules.displayText(name.get(), config.nameMaxDisplayChars());
            float up = DisplayLayout.nameOffset(icon.isPresent());
            spawned.add(block.getWorld().spawn(at, TextDisplay.class, display -> {
                prepare(display, config);
                display.text(Component.text(text));
                display.setShadowed(true);
                display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                display.setViewRange(viewRange(config.nameViewRange()));
                display.setTransformation(transform(rotation, up, DisplayLayout.NAME_SCALE, shift));
            }));
        }
        boolean outside = at.getBlockX() >> 4 != block.getX() >> 4 || at.getBlockZ() >> 4 != block.getZ() >> 4;
        LidDisplays lid = null;
        if (onLid) {
            List<Rest> rests = new ArrayList<>(spawned.size());
            for (Entity entity : spawned) {
                Display display = (Display) entity;
                rests.add(new Rest(display, display.getTransformation()));
            }
            Vector3f standOffset = at.toVector().subtract(block.getLocation().add(0.5, 0.5, 0.5).toVector()).toVector3f();
            lid = new LidDisplays(face.getDirection().toVector3f(), standOffset, rests);
        }
        Drawn drawn = new Drawn(spawned, drawIcon, drawName, false, outside, lid);
        displays.put(key, drawn);
        LidState state = lids.get(key);
        boolean openByAnyone = box.getInventory().getViewers().stream()
                .anyMatch(v -> v.getGameMode() != org.bukkit.GameMode.SPECTATOR);
        if (state != null && state.task == null && !openByAnyone) {
            lids.remove(key); // left open by a close we never saw; the lid is shut
            state = null;
        }
        if (lid != null && state != null && state.tenths > 0) {
            poseLid(lid, state.tenths, false); // drawn while the box is (partly) open
        }
    }

    /** Puts the lid-face displays where the lid is at {@code tenths}/10 open; over one tick if {@code smooth}. */
    private static void poseLid(LidDisplays lid, int tenths, boolean smooth) {
        float progress = tenths / 10f;
        for (Rest rest : lid.rests()) {
            Display display = rest.display();
            if (!Bukkit.isOwnedByCurrentRegion(display) || !display.isValid()) {
                continue;
            }
            Transformation at = rest.transformation();
            LidMotion.Pose pose = LidMotion.at(progress, lid.facing(), lid.standOffset(),
                    at.getTranslation(), at.getLeftRotation());
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(smooth ? 1 : 0);
            display.setTransformation(new Transformation(pose.translation(), pose.leftRotation(),
                    at.getScale(), at.getRightRotation()));
        }
    }

    /**
     * Region thread. The box was opened. Its lid starts to rise: lid-face displays follow it, and
     * side displays are hidden until it closes (if {@code display.hide_while_open}).
     */
    void opened(BlockKey key) {
        moveLid(key, true);
        Drawn drawn = displays.get(key);
        if (drawn != null && drawn.lid() == null && settings.config().hideWhileOpen()) {
            hideOpen(key);
        }
    }

    /**
     * Region thread. The last (non-spectator) viewer closed the box: the lid comes down, and side
     * displays hidden while it was open are drawn again once it's shut.
     */
    void closed(BlockKey key, long lidCloseTicks) {
        Location location = key.location();
        if (location == null || !(location.getBlock().getState(false) instanceof ShulkerBox)) {
            stopLid(key); // removed while open without an event we see (/setblock, WorldEdit)
            return;
        }
        moveLid(key, false);
        Drawn drawn = displays.get(key);
        if (drawn == null || drawn.lid() == null) {
            refreshLater(key, lidCloseTicks);
        }
    }

    /**
     * Starts the lid moving towards open or shut, one tenth per tick, the first step at the start
     * of the next tick (the one in which vanilla starts the lid). Only for boxes we draw.
     */
    private void moveLid(BlockKey key, boolean opening) {
        Location location = key.location();
        if (location == null || (!tracked.containsKey(key) && !lids.containsKey(key))) {
            return;
        }
        LidState state = lids.computeIfAbsent(key, k -> new LidState());
        state.opening = opening;
        if (state.task != null) {
            return; // already moving: a reversal turns at its next step
        }
        // Players open and close boxes between ticks; vanilla starts the lid in the next tick,
        // whose scheduler pass (where this first runs) comes before entity changes are sent. So
        // each step is sent with the lid's own step for that tick. Stepping at the event as well
        // put the displays a step ahead: closing, inside the lid.
        state.task = plugin.getServer().getRegionScheduler().runAtFixedRate(plugin, location, task -> {
            LidState current = lids.get(key);
            if (current == null || current.task != task || stepLid(key, current)) {
                if (current != null && current.task == task) {
                    current.task = null;
                }
                cancelQuietly(task);
            }
        }, 1, 1);
    }

    /** Moves the lid one tenth and poses its displays; true when it has reached open or shut. */
    private boolean stepLid(BlockKey key, LidState state) {
        state.tenths = Math.clamp(state.tenths + (state.opening ? 1 : -1), 0, 10);
        Drawn drawn = displays.get(key);
        if (drawn != null && drawn.lid() != null) {
            poseLid(drawn.lid(), state.tenths, true);
        }
        boolean done = state.tenths == (state.opening ? 10 : 0);
        if (done && state.tenths == 0) {
            lids.remove(key);
        }
        return done;
    }

    /** Lid progress in tenths (0 shut, 10 open); for tests. */
    int lidTenths(BlockKey key) {
        LidState state = lids.get(key);
        return state == null ? 0 : state.tenths;
    }

    /** Whether the box's displays are on its lid face and move with it; for tests. */
    boolean onLid(BlockKey key) {
        Drawn drawn = displays.get(key);
        return drawn != null && drawn.lid() != null;
    }

    private void stopLid(BlockKey key) {
        LidState state = lids.remove(key);
        if (state != null && state.task != null) {
            cancelQuietly(state.task);
        }
    }

    private List<Vector> playersAround(Block block, StickerConfig config) {
        if (forceNearby) {
            return List.of(block.getLocation().add(0.5, 0.5, 0.5).toVector());
        }
        int range = Math.max(config.iconViewRange(), config.nameViewRange());
        return nearby.players(block, range, range);
    }

    private static boolean inRange(Block block, List<Vector> players, int range) {
        Vector center = block.getLocation().add(0.5, 0.5, 0.5).toVector();
        double max = (double) range * range;
        for (Vector p : players) {
            if (p.distanceSquared(center) <= max) {
                return true;
            }
        }
        return false;
    }

    /** One repeating check per chunk with tracked boxes, on that chunk's region. */
    private void ensureCheck(BlockKey key) {
        ChunkKey chunk = ChunkKey.of(key);
        checks.computeIfAbsent(chunk, c -> {
            World world = Bukkit.getWorld(c.world());
            if (world == null) {
                return null;
            }
            return plugin.getServer().getRegionScheduler().runAtFixedRate(plugin, world, c.x(), c.z(),
                    task -> check(c, task), CHECK_TICKS, CHECK_TICKS);
        });
    }

    /** Region thread: spawns or removes displays as players come within or leave view range. */
    private void check(ChunkKey chunk, ScheduledTask task) {
        long start = System.nanoTime();
        try {
            checkChunk(chunk, task);
        } finally {
            checkNanos.addAndGet(System.nanoTime() - start);
        }
    }

    /** Total time spent in the once-a-second proximity checks (read by the perf test). */
    private final java.util.concurrent.atomic.AtomicLong checkNanos = new java.util.concurrent.atomic.AtomicLong();

    long checkNanos() {
        return checkNanos.get();
    }

    private void checkChunk(ChunkKey chunk, ScheduledTask task) {
        List<BlockKey> keys = List.copyOf(byChunk.getOrDefault(chunk, java.util.Set.of()));
        World world = Bukkit.getWorld(chunk.world());
        if (world == null || !world.isChunkLoaded(chunk.x(), chunk.z())) {
            // Unloaded without (or after) its unload event: forget everything in it.
            removeChunk(chunk.world(), chunk.x(), chunk.z());
            if (task != null) {
                checks.remove(chunk, task);
                cancelQuietly(task);
            }
            return;
        }
        if (keys.isEmpty()) {
            if (task != null) {
                checks.remove(chunk, task);
                cancelQuietly(task);
            }
            return;
        }
        StickerConfig config = settings.config();
        int maxRange = Math.max(config.iconViewRange(), config.nameViewRange());
        int minY = keys.stream().mapToInt(BlockKey::y).min().orElseThrow();
        int maxY = keys.stream().mapToInt(BlockKey::y).max().orElseThrow();
        Block center = world.getBlockAt((chunk.x() << 4) + 8, (minY + maxY) / 2, (chunk.z() << 4) + 8);
        // One player lookup per chunk, just big enough to cover every tracked box in it.
        List<Vector> players = forceNearby ? null
                : nearby.players(center, maxRange + 12, maxRange + (maxY - minY) / 2.0 + 1);
        for (BlockKey key : keys) {
            Parts parts = tracked.get(key);
            Drawn drawn = displays.get(key);
            if (parts == null || (drawn != null && drawn.hiddenOpen())) {
                continue;
            }
            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            boolean wantIcon = parts.icon() && (players == null || inRange(block, players, config.iconViewRange()));
            boolean wantName = parts.name() && (players == null || inRange(block, players, config.nameViewRange()));
            boolean hasIcon = drawn != null && drawn.icon();
            boolean hasName = drawn != null && drawn.name();
            // A display standing in a neighbouring chunk is discarded when that chunk unloads; draw it again.
            // Only displays standing in a neighbouring chunk can be lost this way, so only those are looked at.
            boolean lost = drawn != null && drawn.outside() && drawn.entities().stream()
                    .anyMatch(e -> !Bukkit.isOwnedByCurrentRegion(e) || !e.isValid());
            if (wantIcon != hasIcon || wantName != hasName || (lost && (wantIcon || wantName))) {
                refresh(block);
            }
        }
    }

    private static void cancelQuietly(ScheduledTask task) {
        if (task == null) {
            return;
        }
        try {
            task.cancel();
        } catch (RuntimeException e) {
            // MockBukkit doesn't implement cancel (its exception isn't an UnsupportedOperationException); real servers do.
        }
    }

    /** Region thread only. Removes the box's displays; it stays tracked. */
    void remove(BlockKey key) {
        removeEntities(key);
    }

    /** Region thread only. The box was opened: hide its displays until it closes (PRD §5.5). */
    void hideOpen(BlockKey key) {
        removeEntities(key);
        if (tracked.containsKey(key)) {
            displays.put(key, new Drawn(List.of(), false, false, true, false));
        }
    }

    /** Region thread only. Removes the displays and stops tracking the box (it was broken or moved). */
    void forget(BlockKey key) {
        removeEntities(key);
        untrack(key);
        stopLid(key);
    }

    private void track(BlockKey key, Parts parts) {
        tracked.put(key, parts);
        byChunk.computeIfAbsent(ChunkKey.of(key), c -> ConcurrentHashMap.newKeySet()).add(key);
    }

    private void untrack(BlockKey key) {
        tracked.remove(key);
        byChunk.computeIfPresent(ChunkKey.of(key), (c, keys) -> {
            keys.remove(key);
            return keys.isEmpty() ? null : keys;
        });
    }

    private void removeEntities(BlockKey key) {
        Drawn old = displays.remove(key);
        if (old != null) {
            old.entities().forEach(Entity::remove);
        }
    }

    /** Region thread only. Forgets and removes every display in a chunk. */
    void removeChunk(UUID world, int chunkX, int chunkZ) {
        ChunkKey chunk = new ChunkKey(world, chunkX, chunkZ);
        java.util.Set<BlockKey> keys = new java.util.HashSet<>(byChunk.getOrDefault(chunk, java.util.Set.of()));
        // Displays are only drawn for tracked boxes, but sweep them too in case of a race.
        displays.keySet().stream().filter(k -> ChunkKey.of(k).equals(chunk)).forEach(keys::add);
        keys.forEach(this::forget);
        byChunk.remove(chunk);
        ScheduledTask task = checks.remove(chunk);
        if (task != null) {
            cancelQuietly(task);
        }
    }

    /**
     * Plugin disable: removes every display we track, then sweeps loaded chunks for strays
     * tagged as ours. Only entities owned by the current thread are touched. On Folia/Canvas
     * a plugin is disabled only at shutdown, other regions' entities are skipped and the
     * world-wide sweep is unavailable; that is safe because displays are never saved.
     */
    void removeAll() {
        checks.values().forEach(DisplayManager::cancelQuietly);
        checks.clear();
        lids.values().forEach(state -> {
            if (state.task != null) {
                cancelQuietly(state.task);
            }
        });
        lids.clear();
        for (Drawn drawn : displays.values()) {
            drawn.entities().forEach(DisplayManager::removeIfOwned);
        }
        displays.clear();
        tracked.clear();
        byChunk.clear();
        if (FOLIA) {
            return;
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(Display.class)) {
                if (entity.getPersistentDataContainer().has(PaperKeys.DISPLAY)) {
                    removeIfOwned(entity);
                }
            }
        }
    }

    /**
     * Removes an entity only if this thread owns it. Asking about an entity owned by another
     * region can itself fail on Folia while the server shuts down, so any failure just skips
     * it: displays are never saved, so a skipped one disappears with the server.
     */
    private static void removeIfOwned(Entity entity) {
        try {
            if (Bukkit.isOwnedByCurrentRegion(entity) && entity.isValid()) {
                entity.remove();
            }
        } catch (RuntimeException e) {
            // Not ours to touch at this point; see above.
        }
    }

    /** True on Folia and its forks (Canvas), which have no single main thread. */
    static final boolean FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    int count() {
        return displays.values().stream().mapToInt(d -> d.entities().size()).sum();
    }

    /** Test hook: the tracked boxes of one chunk, from the per-chunk index. */
    java.util.Set<BlockKey> trackedInChunk(UUID world, int chunkX, int chunkZ) {
        return java.util.Set.copyOf(byChunk.getOrDefault(new ChunkKey(world, chunkX, chunkZ), java.util.Set.of()));
    }

    /** Test hook: runs one proximity check of a chunk now, on the calling thread. */
    void checkNow(UUID world, int chunkX, int chunkZ) {
        checkChunk(new ChunkKey(world, chunkX, chunkZ), null);
    }

    /** Test hook: true while the box's displays are hidden because someone has it open. */
    boolean hiddenWhileOpen(BlockKey key) {
        Drawn drawn = displays.get(key);
        return drawn != null && drawn.hiddenOpen();
    }

    int trackedCount() {
        return tracked.size();
    }

    List<Entity> displaysAt(Block block) {
        Drawn drawn = displays.get(BlockKey.of(block));
        return drawn == null ? List.of() : drawn.entities();
    }

    private void prepare(Display display, StickerConfig config) {
        display.setPersistent(false);
        display.setBillboard(Display.Billboard.FIXED);
        if (config.iconFullBright()) {
            display.setBrightness(new Display.Brightness(15, 15));
        }
        display.getPersistentDataContainer().set(PaperKeys.DISPLAY, PersistentDataType.BOOLEAN, true);
    }

    /** Display view range is a multiplier of 64 blocks. */
    static float viewRange(int blocks) {
        return blocks / 64f;
    }

    /**
     * The display stands just outside the face with no yaw or pitch; {@link IconOrientation}
     * turns it, and {@code up} moves it along the image's own up direction.
     */
    /** {@code shift} moves the drawing from where the entity stands to where it should appear (world axes; displays keep yaw and pitch 0). */
    private static Transformation transform(Quaternionf rotation, float up, float scale, Vector3f shift) {
        Vector3f offset = rotation.transform(new Vector3f(0f, up, 0f)).add(shift);
        return new Transformation(offset, new Quaternionf(rotation),
                new Vector3f(scale, scale, scale), new Quaternionf());
    }

    /**
     * Where to spawn a display meant to appear at {@code wanted}, just in front of a face. For a
     * box in a chunk's last column that spot is in the next chunk. If that chunk is loaded (the
     * usual case: a player is near enough to see the display), the display goes there, where it
     * gets the light in front of the face. If it isn't, spawning there would put the entity in an
     * unloaded chunk (on Folia possibly another region's), so it stands just inside the box's own
     * chunk instead and only its drawing is moved out; it is then one light level darker, because
     * the box dims light by one.
     */
    static Location spawnSpot(Location wanted, Block box) {
        int chunkX = wanted.getBlockX() >> 4;
        int chunkZ = wanted.getBlockZ() >> 4;
        boolean sameChunk = chunkX == box.getX() >> 4 && chunkZ == box.getZ() >> 4;
        boolean neighbourUsable = box.getWorld().isChunkLoaded(chunkX, chunkZ)
                && Bukkit.isOwnedByCurrentRegion(box.getWorld(), chunkX, chunkZ);
        return sameChunk || neighbourUsable ? wanted : insideOwnChunk(wanted, box.getX(), box.getZ());
    }

    /** {@code wanted}, moved just inside the chunk of the box at {@code boxX, boxZ} if it lies past the chunk's edge. */
    static Location insideOwnChunk(Location wanted, int boxX, int boxZ) {
        double minX = (boxX >> 4) << 4;
        double minZ = (boxZ >> 4) << 4;
        double margin = 1.0E-3;
        Location at = wanted.clone();
        at.setX(Math.clamp(wanted.getX(), minX + margin, minX + 16 - margin));
        at.setZ(Math.clamp(wanted.getZ(), minZ + margin, minZ + 16 - margin));
        return at;
    }
}
