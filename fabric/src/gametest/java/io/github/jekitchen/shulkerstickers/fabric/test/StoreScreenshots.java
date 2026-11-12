package io.github.jekitchen.shulkerstickers.fabric.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Store screenshots, taken by a real 26.3 client in singleplayer with the mod installed (the
 * integrated server runs it exactly as a dedicated server would). Run with
 * {@code ./gradlew :fabric:runClientGameTest}; the pictures are copied to
 * {@code docs/images/screenshots/}. Not part of {@code build}: it needs a display.
 */
public final class StoreScreenshots implements FabricClientGameTest {

    /** Where the pictures go, relative to the client's run folder (fabric/build/run/clientGameTest). */
    private static final Path OUT = Path.of("../../../../docs/images/screenshots");

    /** The editor's "use off-hand item" slot, in window pixels at 1600x900 and automatic GUI scale. */
    private static final int EDITOR_ICON_SLOT_X = 692;
    private static final int EDITOR_ICON_SLOT_Y = 385;

    private static final String[][] WALL = {
            // color, icon, name
            {"light_gray", "cobblestone", "Stone"}, {"brown", "oak_log", "Wood"}, {"white", "iron_ingot", "Iron"},
            {"yellow", "gold_ingot", "Gold"}, {"red", "redstone", "Redstone"}, {"cyan", "diamond", "Gems"},
            {"orange", "bread", "Food"}, {"green", "wheat_seeds", "Farm"}, {"black", "coal", "Fuel"},
            {"blue", "lapis_lazuli", "Lapis"}, {"purple", "ender_pearl", "Pearls"}, {"lime", "slime_ball", "Slime"},
            {"gray", "gunpowder", "Boom"}, {"pink", "string", "String"}, {"magenta", "glass", "Glass"},
            {"light_blue", "arrow", "Arrows"}, {"", "torch", "Torches"}, {"white", "bone", "Bones"},
    };

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1600, 900);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            int ground = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule advance_weather false");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("gamemode creative @a");
            server.runCommand("fill -6 " + (ground - 1) + " -2 6 " + (ground - 1) + " 8 minecraft:polished_andesite");
            buildWall(server, ground);
            buildFloor(server, ground);

            context.getInput().pressKey(options -> options.keyToggleGui); // F1: hide the HUD

            // 1. A storage wall: six columns, three rows, every box with an icon and a name.
            server.runCommand("tp @p 0.5 " + (ground + 0.4) + " 4.6");
            settle(context);
            context.getInput().lookAt(new BlockPos(0, ground + 1, 0));
            context.waitTicks(5);
            save(context, "wall");

            // 2. Boxes on the floor, seen from above: icons on lids, one turned, one on a side.
            // Seen from the north: a lid icon with rotation 0 reads upright from that side.
            // Standing on a barrier (invisible) so the camera stays up instead of falling.
            server.runCommand("setblock 21 " + (ground + 1) + " -3 minecraft:barrier");
            server.runCommand("tp @p 21.5 " + (ground + 2) + " -2.5");
            settle(context);
            context.getInput().lookAt(new BlockPos(21, ground - 1, 0));
            context.waitTicks(5);
            save(context, "floor");

            // 3. The Sticker editor: sneak and right-click a box with an empty main hand, holding
            // the icon to stick in the off-hand.
            context.getInput().pressKey(options -> options.keyToggleGui); // F1 again: show it
            server.runCommand("clear @p");
            server.runCommand("item replace entity @p weapon.offhand with minecraft:diamond");
            server.runCommand("setblock 40 " + ground + " 0 minecraft:purple_shulker_box[facing=up]");
            server.runCommand("tp @p 40.5 " + ground + " 2.5");
            context.waitTicks(220); // the recipe toast for the diamond fades
            context.getInput().holdKey(options -> options.keyShift);
            context.waitTicks(5);
            // Teleporting doesn't reliably turn the camera, so aim it directly.
            context.getInput().lookAt(new BlockPos(40, ground, 0));
            context.waitTicks(2);
            context.getInput().pressKey(options -> options.keyUse); // right-click
            context.waitForScreen(AbstractContainerScreen.class);
            context.getInput().releaseKey(options -> options.keyShift);
            context.waitTicks(10);
            context.getInput().setCursorPos(EDITOR_ICON_SLOT_X, EDITOR_ICON_SLOT_Y); // shows its tooltip
            context.waitTicks(5);
            save(context, "editor");
        }
    }

    /** 18 boxes in a 6x3 wall facing south, toward the camera. */
    private static void buildWall(TestServerContext server, int ground) {
        for (int i = 0; i < WALL.length; i++) {
            int x = -3 + i % 6;
            int y = ground + 2 - i / 6;
            String[] box = WALL[i];
            server.runCommand("setblock " + x + " " + y + " 0 " + decorated(box[0], "south", box[1], "lid", 0, box[2]));
        }
    }

    /** Boxes facing up on the floor: lid icons, one rotated, one on its north side. */
    private static void buildFloor(TestServerContext server, int ground) {
        List<String> boxes = List.of(
                decorated("red", "up", "redstone", "lid", 0, "Redstone"),
                decorated("cyan", "up", "diamond_pickaxe", "lid", 1, "Tools"),
                decorated("lime", "up", "emerald", "lid", 0, "Trades"),
                decorated("yellow", "up", "golden_apple", "side", 0, "Snacks"));
        for (int i = 0; i < boxes.size(); i++) {
            server.runCommand("setblock " + (18 + i * 2) + " " + ground + " 0 " + boxes.get(i));
        }
    }

    /** A box with a sticker, in the storage layout both platforms read (see the showcase datapack). */
    private static String decorated(String color, String facing, String icon, String anchor, int rot, String name) {
        String block = color.isEmpty() ? "shulker_box" : color + "_shulker_box";
        String side = anchor.equals("side") ? ",\"shulker_stickers:face\":\"north\"" : "";
        return "minecraft:" + block + "[facing=" + facing + "]{PublicBukkitValues:{"
                + "\"shulker_stickers:v\":1,\"shulker_stickers:icon_id\":\"minecraft:" + icon + "\","
                + "\"shulker_stickers:anchor\":\"" + anchor + "\",\"shulker_stickers:rot\":" + rot + side
                + "},CustomName:\"" + name + "\"}";
    }

    /** Lets chunks render and displays arrive and settle. */
    private static void settle(ClientGameTestContext context) {
        context.waitTicks(60);
    }

    private static void save(ClientGameTestContext context, String name) {
        Path shot = context.takeScreenshot(name);
        try {
            Files.createDirectories(OUT);
            Files.copy(shot, OUT.resolve(name + ".png"), StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("couldn't copy " + shot, e);
        }
    }
}
