package io.github.jekitchen.shulkerstickers.fabric.test;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.fabric.ShulkerStickersFabric;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import io.github.jekitchen.shulkerstickers.fabric.display.StickerDisplays;
import io.github.jekitchen.shulkerstickers.fabric.editor.BoxEdits;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/**
 * AC-8 groundwork: server cost of 1,000 decorated boxes. Opt-in, because it takes ~25 s:
 * {@code ./gradlew :fabric:runGameTest -Pperftest}. Without the flag it passes at once.
 *
 * <p>Results are logged as {@code PERF ...} lines.
 */
public class PerfGameTests {

    private static final boolean ENABLED = Boolean.getBoolean("shulkerstickers.perftest");
    private static final int WIDTH = 50;
    private static final int HEIGHT = 20;
    /** Let the other GameTests finish first, so they don't add noise. */
    private static final int WARMUP = 200;
    private static final int SAMPLE = 100;

    @GameTest(maxTicks = 1200, required = false)
    public void thousandDecoratedBoxes(GameTestHelper helper) {
        if (!ENABLED) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        // Well above the test area, so no other test's structure overlaps the wall.
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(100);
        List<BlockPos> wall = new ArrayList<>();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                wall.add(base.offset(x, y, 0));
            }
        }

        helper.runAfterDelay(WARMUP, () -> {
            TickRecorder.start();
            helper.runAfterDelay(SAMPLE, () -> {
                long[] baseline = TickRecorder.stop();
                long placeStart = System.nanoTime();
                Sticker sticker = new Sticker(Optional.of("minecraft:cobblestone"), Optional.empty(), Anchor.LID, 0, Optional.empty());
                for (int i = 0; i < wall.size(); i++) {
                    BlockPos pos = wall.get(i);
                    level.setBlock(pos, Blocks.SHULKER_BOX.defaultBlockState().setValue(ShulkerBoxBlock.FACING, Direction.SOUTH),
                            Block.UPDATE_CLIENTS);
                    if (level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box) {
                        StickerData.write(box, Optional.of(sticker));
                        BoxEdits.setName(box, Component.literal("Storage " + i));
                        StickerDisplays.refresh(level, pos);
                    }
                }
                long placeNanos = System.nanoTime() - placeStart;
                int drawn = (int) wall.stream().filter(p -> StickerDisplays.isShown(level, p)).count();

                // A watching player in front of the wall, so Polymer actually sends the displays.
                ServerPlayer watcher = helper.makeMockServerPlayerInLevel();
                watcher.teleportTo(base.getX() + WIDTH / 2.0, base.getY() + HEIGHT / 2.0, base.getZ() + 8.0);

                helper.runAfterDelay(20, () -> {
                    TickRecorder.start();
                    long[] hook = new long[200];
                    for (int i = 0; i < hook.length; i++) {
                        long t = System.nanoTime();
                        StickerDisplays.updateOpenBoxes(level);
                        hook[i] = System.nanoTime() - t;
                    }
                    helper.runAfterDelay(SAMPLE, () -> {
                        long[] withBoxes = TickRecorder.stop();
                        log("boxes placed and drawn: %d/%d in %.1f ms (one-off)", drawn, wall.size(), placeNanos / 1e6);
                        log("mod per-tick hook (updateOpenBoxes, 1,000 boxes): mean %.4f ms, p99 %.4f ms, max %.4f ms",
                                mean(hook), pct(hook, 0.99), pct(hook, 1.0));
                        log("server tick without boxes: mean %.3f ms, p99 %.3f ms", mean(baseline), pct(baseline, 0.99));
                        log("server tick with 1,000 boxes + 1 watcher: mean %.3f ms, p99 %.3f ms",
                                mean(withBoxes), pct(withBoxes, 0.99));
                        log("difference in mean server tick: %.3f ms", mean(withBoxes) - mean(baseline));
                        wall.forEach(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS));
                        watcher.discard();
                        helper.succeed();
                    });
                });
            });
        });
    }

    /** Records the duration of every server tick while {@link #recording} is set. */
    private static final class TickRecorder {
        private static long tickStart;
        private static List<Long> samples;
        /** start() runs mid-tick, so the tick it was called in is skipped. */
        private static boolean skipCurrent;

        static {
            ServerTickEvents.START_SERVER_TICK.register(server -> tickStart = System.nanoTime());
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                if (samples != null) {
                    if (skipCurrent) {
                        skipCurrent = false;
                    } else {
                        samples.add(System.nanoTime() - tickStart);
                    }
                }
            });
        }

        static void start() {
            skipCurrent = true;
            samples = new ArrayList<>();
        }

        static long[] stop() {
            long[] out = samples.stream().mapToLong(Long::longValue).toArray();
            samples = null;
            return out;
        }
    }

    private static double mean(long[] nanos) {
        return Arrays.stream(nanos).average().orElse(0) / 1e6;
    }

    private static double pct(long[] nanos, double p) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        int i = (int) Math.min(sorted.length - 1, Math.ceil(p * sorted.length) - 1);
        return sorted[Math.max(0, i)] / 1e6;
    }

    private static void log(String format, Object... args) {
        ShulkerStickersFabric.LOGGER.info("PERF " + String.format(format, args));
    }
}
