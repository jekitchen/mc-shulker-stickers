package io.github.jekitchen.shulkerstickers.paper;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * AC-8 groundwork, enabled with {@code -Dshulkerstickers.perftest=true} ({@code -Pperftest}):
 * builds a 50 × 20 wall of 1,000 stickered, named boxes and logs {@code PERF ...} lines with the
 * server's tick cost before and after. The plugin itself has no per-tick work for idle boxes
 * (everything is event-driven), so the cost measured here is mostly the 2,000 display entities.
 *
 * <p>Paper reports per-tick durations through {@link ServerTickEndEvent}; Folia and Canvas have
 * no global tick, so there we log their {@code /tps} region report instead.
 */
final class PerfTest implements Listener {

    private static final int WIDTH = 50;
    private static final int HEIGHT = 20;
    private static final int SAMPLE = 200;
    /** Control run: the same wall with no stickers or names, i.e. vanilla's own cost. */
    private static final boolean PLAIN = Boolean.getBoolean("shulkerstickers.perftest.plain");
    /** Variant: icons only, no names (one display entity per box). */
    private static final boolean NO_NAMES = Boolean.getBoolean("shulkerstickers.perftest.nonames");
    /** Simulate a player within view range of every box; without it the run has no player at all. */
    private static final boolean NEARBY = Boolean.getBoolean("shulkerstickers.perftest.nearby");

    private final Plugin plugin;
    private final DisplayManager displays;
    private volatile List<Double> samples;

    PerfTest(Plugin plugin, DisplayManager displays) {
        this.plugin = plugin;
        this.displays = displays;
    }

    void schedule() {
        displays.forceNearby(NEARBY);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> start(), 100);
    }

    @EventHandler
    void onTickEnd(ServerTickEndEvent event) {
        List<Double> current = samples;
        if (current != null) {
            current.add(event.getTickDuration());
        }
    }

    private void start() {
        World world = Bukkit.getWorlds().getFirst();
        Location origin = new Location(world, 0, 200, 8);
        // Chunks x 0..3, z 0: one Folia region section, kept loaded for the whole run.
        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (int cx = 0; cx < 4; cx++) {
            loads.add(world.getChunkAtAsync(cx, 0).thenAccept(c -> c.addPluginChunkTicket(plugin)));
        }
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenRun(() -> {
            report("before");
            recordWindow("before", () ->
                    Bukkit.getRegionScheduler().execute(plugin, origin, () -> build(world, origin)));
        });
    }

    /** Region thread. */
    private void build(World world, Location origin) {
        Sticker sticker = new Sticker(Optional.of("minecraft:cobblestone"), Optional.empty(), Anchor.LID, 0, Optional.empty());
        List<Block> wall = new ArrayList<>();
        long start = System.nanoTime();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                Block block = world.getBlockAt(origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ());
                Directional data = (Directional) Material.SHULKER_BOX.createBlockData();
                data.setFacing(BlockFace.SOUTH);
                block.setType(Material.AIR, false);
                block.setBlockData(data, false);
                if (!PLAIN) {
                    ShulkerBox state = (ShulkerBox) block.getState();
                    if (!NO_NAMES) {
                        state.customName(Component.text("Storage " + wall.size()));
                    }
                    StickerStore.write(state.getPersistentDataContainer(), sticker);
                    state.update(true, false);
                }
                wall.add(block);
            }
        }
        long placed = System.nanoTime();
        if (!Boolean.getBoolean("shulkerstickers.perftest.notrack")) {
            wall.forEach(displays::refresh);
        }
        long drawn = System.nanoTime();
        log("%s%s1,000 boxes placed in %.1f ms; displays spawned in %.1f ms (one-off, like a chunk load); "
                        + "tracked boxes: %d; display entities: %d",
                PLAIN ? "CONTROL (plain boxes) " : "", NEARBY ? "PLAYER NEARBY (simulated) " : "NO PLAYER ",
                (placed - start) / 1e6, (drawn - placed) / 1e6, displays.trackedCount(), displays.count());

        // Re-drawing everything is what a chunk reload or /shulkerstickers reload costs.
        long again = System.nanoTime();
        if (!Boolean.getBoolean("shulkerstickers.perftest.notrack")) {
            wall.forEach(displays::refresh);
        }
        log("re-drawing all 1,000 boxes: %.1f ms (one-off)", (System.nanoTime() - again) / 1e6);

        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> {
            report("after");
            recordWindow("after", () -> Bukkit.getRegionScheduler().execute(plugin, origin, () -> {
                wall.forEach(b -> b.setType(Material.AIR, false));
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t2 -> {
                    log("done");
                    if (Boolean.getBoolean("shulkerstickers.selftest.stop")) {
                        Bukkit.shutdown();
                    }
                }, 20);
            }));
        }, 200);
    }

    /** Records {@link #SAMPLE} tick durations (Paper only), logs them, then runs {@code next}. */
    private void recordWindow(String label, Runnable next) {
        samples = new ArrayList<>();
        long checksBefore = displays.checkNanos();
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> {
            log("%s: plugin proximity checks: %.4f ms per tick on average", label,
                    (displays.checkNanos() - checksBefore) / 1e6 / SAMPLE);
            double[] ticks = samples.stream().mapToDouble(Double::doubleValue).toArray();
            samples = null;
            if (ticks.length == 0) {
                log("%s: no per-tick timings on this server (Folia/Canvas); see the /tps report above", label);
            } else {
                double[] sorted = ticks.clone();
                Arrays.sort(sorted);
                log("%s: server tick over %d ticks: mean %.3f ms, p99 %.3f ms", label, ticks.length,
                        Arrays.stream(ticks).average().orElse(0), sorted[(int) Math.ceil(0.99 * sorted.length) - 1]);
            }
            next.run();
        }, SAMPLE);
    }

    /** Global region thread: logs the server's own tick report. */
    private void report(String label) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            log("%s: server report follows", label);
            boolean folia = Bukkit.getName().toLowerCase().contains("folia") || Bukkit.getName().toLowerCase().contains("canvas");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), folia ? "tps" : "mspt");
        });
    }

    private void log(String format, Object... args) {
        plugin.getLogger().info("PERF " + String.format(format, args));
    }
}
