package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Owner decision 5: warn when another world datapack overrides the shulker box loot tables. */
class LootTableGuardTest {

    @Test
    void findsFolderAndZipPacksThatOverrideShulkerTables(@TempDir Path datapacks) throws IOException {
        Path folder = datapacks.resolve("better_drops/data/minecraft/loot_table/blocks");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("red_shulker_box.json"), "{}");

        Path unrelated = datapacks.resolve("trees/data/minecraft/loot_table/blocks");
        Files.createDirectories(unrelated);
        Files.writeString(unrelated.resolve("oak_leaves.json"), "{}");

        try (OutputStream out = Files.newOutputStream(datapacks.resolve("zipped.zip"));
                ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("data/minecraft/loot_table/blocks/shulker_box.json"));
            zip.write("{}".getBytes());
            zip.closeEntry();
        }

        List<String> found = LootTableGuard.overridingPacks(datapacks).stream().sorted().toList();
        assertEquals(List.of("better_drops", "zipped.zip"), found);
    }

    @Test
    void missingFolderReportsNothing(@TempDir Path dir) {
        assertEquals(List.of(), LootTableGuard.overridingPacks(dir.resolve("datapacks")));
    }
}
