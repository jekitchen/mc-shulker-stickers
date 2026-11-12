package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * AC-7: {@code /shulkerstickers reload} picks up a changed config file. Paper's command API
 * doesn't run under MockBukkit, so this calls the command's handler directly.
 */
class ReloadCommandTest {

    @TempDir
    Path dir;

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void reloadAppliesAChangedConfigFile() throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{}");
        Settings settings = new Settings(file, Logger.getLogger("test"));
        settings.reload();
        assertTrue(settings.config().colorEnabled());
        assertEquals(List.of(), settings.config().nameBlockedWords());

        StickerCommands commands = new StickerCommands(plugin, settings, new DisplayManager(plugin, settings));
        PlayerMock admin = server.addPlayer();
        admin.setOp(true);
        Files.writeString(file, "{\"color\": {\"enabled\": false}, \"name\": {\"blocked_words\": [\"grief\"]}}");

        commands.reload(admin);

        assertFalse(settings.config().colorEnabled(), "color.enabled from the new file");
        assertEquals(List.of("grief"), settings.config().nameBlockedWords(), "name.blocked_words from the new file");
        assertEquals("Shulker Stickers config reloaded",
                PlainTextComponentSerializer.plainText().serialize(admin.nextComponentMessage()));
    }

    @Test
    void reloadWithAMistakeSaysHowManyWarnings() throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{}");
        Settings settings = new Settings(file, Logger.getLogger("test"));
        settings.reload();
        StickerCommands commands = new StickerCommands(plugin, settings, new DisplayManager(plugin, settings));
        PlayerMock admin = server.addPlayer();
        Files.writeString(file, "{\"color\": {\"enabled\": \"yes please\"}}");

        commands.reload(admin);

        assertEquals("Config reloaded with 1 warning(s); see the server log",
                PlainTextComponentSerializer.plainText().serialize(admin.nextComponentMessage()));
    }
}
