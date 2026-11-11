package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.BoxState;
import io.github.jekitchen.shulkerstickers.core.EditorSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The Sticker editor (PRD §5.2) and its rename screen (§5.4).
 *
 * <p>Threading (Folia/Canvas): inventory events arrive on the player's region thread,
 * so menus are opened there or through the player's entity scheduler. Reading and
 * changing the box always goes through the region scheduler for the box's location.
 */
final class EditorService implements Listener {

    /** One player's open editor. Fields are only touched from that player's thread. */
    static final class Session {
        final UUID player;
        final BlockKey box;
        final BlockFace clickedFace;
        final int rotation;
        final EditorSession editor;
        String stagedIconVisual = "{}";
        boolean renaming;
        /** Set while we close the menu on purpose (Save, or switching screens). */
        boolean closingOnPurpose;
        AnvilView renameView;
        /** The editor menu currently shown to the player, if any. */
        Inventory editorInventory;

        Session(UUID player, BlockKey box, BlockFace clickedFace, int rotation, EditorSession editor) {
            this.player = player;
            this.box = box;
            this.clickedFace = clickedFace;
            this.rotation = rotation;
            this.editor = editor;
        }
    }

    private final Plugin plugin;
    private final DisplayManager displays;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<BlockKey, UUID> locks = new ConcurrentHashMap<>();

    EditorService(Plugin plugin, DisplayManager displays) {
        this.plugin = plugin;
        this.displays = displays;
    }

    // ---- opening -------------------------------------------------------------------------

    /** Called on the player's (and, in reach, the box's) region thread. */
    void open(Player player, Block block, BlockFace clickedFace) {
        BlockKey key = BlockKey.of(block);
        UUID holder = locks.putIfAbsent(key, player.getUniqueId());
        if (holder != null && !holder.equals(player.getUniqueId())) {
            Player other = plugin.getServer().getPlayer(holder);
            player.sendActionBar(Component.text("Being edited by " + (other == null ? "another player" : other.getName()),
                    NamedTextColor.RED));
            return;
        }
        if (!(block.getState() instanceof ShulkerBox box)) {
            locks.remove(key, player.getUniqueId());
            return;
        }
        BoxColor color = BoxColor.fromItemId(block.getType().getKey().toString()).orElse(BoxColor.UNDYED);
        Optional<String> icon = StickerStore.read(box.getPersistentDataContainer()).map(StickerData::iconId);
        Optional<String> name = Optional.ofNullable(box.customName())
                .map(c -> PlainTextComponentSerializer.plainText().serialize(c));
        int rotation = Math.floorMod(Math.round(player.getYaw() / 90f), 4);
        Session session = new Session(player.getUniqueId(), key, clickedFace, rotation,
                new EditorSession(new BoxState(color, icon, name)));
        sessions.put(player.getUniqueId(), session);
        showEditor(player, session);
        player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 0.6f, 1.2f);
    }

    private void showEditor(Player player, Session session) {
        EditorHolder holder = new EditorHolder(session);
        String title = session.editor.staged().name().orElse("Shulker Box");
        Inventory inventory = plugin.getServer().createInventory(holder, EditorLayout.SIZE,
                Component.text("Edit: " + title));
        holder.inventory(inventory);
        session.editorInventory = inventory;
        render(inventory, session, player);
        session.renaming = false;
        player.openInventory(inventory);
        session.closingOnPurpose = false;
    }

    private void render(Inventory inventory, Session session, Player player) {
        BoxState staged = session.editor.staged();
        for (BoxColor color : BoxColor.values()) {
            ItemStack item = new ItemStack(material(color));
            item.editMeta(meta -> {
                meta.itemName(Component.text(pretty(color)));
                meta.setEnchantmentGlintOverride(color == staged.color());
            });
            inventory.setItem(color.ordinal(), item);
        }
        ItemStack preview = new ItemStack(material(staged.color()));
        preview.editMeta(meta -> {
            meta.itemName(Component.text(staged.name().orElse("Shulker Box")));
            meta.lore(List.of(Component.text("Icon: " + staged.iconId().orElse("none"), NamedTextColor.GRAY)));
        });
        inventory.setItem(EditorLayout.PREVIEW, preview);

        ItemStack offhand = player.getInventory().getItemInOffHand();
        ItemStack useOffhand = offhand.isEmpty() ? new ItemStack(Material.GRAY_STAINED_GLASS_PANE) : offhand.asOne();
        useOffhand.editMeta(meta -> meta.itemName(Component.text(
                offhand.isEmpty() ? "Off-hand is empty" : "Use off-hand item")));
        inventory.setItem(EditorLayout.USE_OFFHAND, useOffhand);
        inventory.setItem(EditorLayout.REMOVE_ICON, button(Material.BARRIER, "Remove icon"));
        inventory.setItem(EditorLayout.RENAME, button(Material.NAME_TAG, "Rename…"));
        inventory.setItem(EditorLayout.CANCEL, button(Material.RED_CONCRETE, "Cancel"));
        inventory.setItem(EditorLayout.SAVE, button(Material.LIME_CONCRETE, "Save"));
    }

    // ---- editor clicks -------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    void onClick(InventoryClickEvent event) {
        if (event.getView() instanceof AnvilView anvil && isRenameView(event.getWhoClicked().getUniqueId(), anvil)) {
            event.setCancelled(true);
            if (event.getRawSlot() == 2 && (event.getClick().isLeftClick() || event.getClick().isRightClick())
                    && !event.getClick().isShiftClick()) {
                finishRename((Player) event.getWhoClicked(), anvil.getRenameText());
            }
            return;
        }
        Session session = editorSession(event.getWhoClicked().getUniqueId(), event.getView());
        if (session == null) {
            return;
        }
        // Anti-duplication: the editor never lets an item move, whatever the click (AC-12).
        event.setCancelled(true);
        if (!EditorLayout.isButtonPress(event.getClick(), event.getRawSlot())) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        EditorLayout.buttonAt(event.getRawSlot()).ifPresent(button -> press(player, session, button));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onDrag(InventoryDragEvent event) {
        if (editorSession(event.getWhoClicked().getUniqueId(), event.getView()) != null
                || (event.getView() instanceof AnvilView anvil
                        && isRenameView(event.getWhoClicked().getUniqueId(), anvil))) {
            event.setCancelled(true);
        }
    }

    /**
     * The session whose editor menu is the top of {@code view}. Looked up by player rather
     * than through {@code getHolder}: cheaper on every click, and it works in MockBukkit.
     */
    private Session editorSession(UUID player, org.bukkit.inventory.InventoryView view) {
        Session session = sessions.get(player);
        return session != null && session.editorInventory != null
                && session.editorInventory.equals(view.getTopInventory()) ? session : null;
    }

    private void press(Player player, Session session, EditorLayout.Button button) {
        switch (button) {
            case EditorLayout.Button.Color c -> session.editor.stageColor(c.color());
            case EditorLayout.Button.UseOffhand u -> {
                // The icon is read from the off-hand only now, on this click (PRD §5.3).
                ItemStack offhand = player.getInventory().getItemInOffHand();
                if (offhand.isEmpty()) {
                    return;
                }
                session.editor.stageIcon(offhand.getType().getKey().toString());
                session.stagedIconVisual = "{}"; // Spike: visual properties not captured yet.
            }
            case EditorLayout.Button.RemoveIcon r -> session.editor.removeIcon();
            case EditorLayout.Button.Rename r -> {
                openRename(player, session);
                return;
            }
            case EditorLayout.Button.Cancel c -> {
                player.closeInventory(); // close handler discards
                return;
            }
            case EditorLayout.Button.Save s -> {
                save(player, session);
                return;
            }
            case EditorLayout.Button.Preview p -> {
                return;
            }
        }
        render(player.getOpenInventory().getTopInventory(), session, player);
    }

    // ---- rename screen -------------------------------------------------------------------

    private void openRename(Player player, Session session) {
        session.closingOnPurpose = true;
        session.renaming = true;
        AnvilView view = MenuType.ANVIL.builder()
                .title(Component.text("Rename box"))
                .checkReachable(false)
                .build(player);
        ItemStack paper = new ItemStack(Material.PAPER);
        paper.editMeta(meta -> meta.customName(Component.text(session.editor.staged().name().orElse(""))));
        view.getTopInventory().setFirstItem(paper);
        view.setRepairCost(0);
        session.renameView = view;
        player.openInventory(view);
        session.closingOnPurpose = false;
    }

    private boolean isRenameView(UUID player, AnvilView view) {
        Session session = sessions.get(player);
        return session != null && session.renaming && session.renameView == view;
    }

    @EventHandler
    void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!isRenameView(event.getView().getPlayer().getUniqueId(), event.getView())) {
            return;
        }
        String text = Objects.requireNonNullElse(event.getView().getRenameText(), "");
        ItemStack result = new ItemStack(Material.PAPER);
        result.editMeta(meta -> meta.customName(Component.text(text)));
        event.setResult(result);
        event.getView().setRepairCost(0);
    }

    private void finishRename(Player player, String text) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.editor.stageName(Objects.requireNonNullElse(text, ""));
        session.closingOnPurpose = true;
        clearRenameView(session);
        showEditor(player, session);
    }

    private void clearRenameView(Session session) {
        if (session.renameView != null) {
            // Our paper must never reach the player's inventory when the anvil closes.
            session.renameView.getTopInventory().clear();
            session.renameView = null;
        }
    }

    // ---- closing -------------------------------------------------------------------------

    @EventHandler
    void onClose(InventoryCloseEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || session.closingOnPurpose) {
            return;
        }
        Player player = (Player) event.getPlayer();
        if (event.getView() instanceof AnvilView anvil && session.renaming && session.renameView == anvil) {
            // Esc on the rename screen goes back to the editor with the name unchanged.
            clearRenameView(session);
            player.getScheduler().run(plugin, t -> {
                if (sessions.get(player.getUniqueId()) == session) {
                    showEditor(player, session);
                }
            }, null);
            return;
        }
        if (editorSession(player.getUniqueId(), event.getView()) == session) {
            // Esc or Cancel: discard (PRD §5.2).
            end(session);
            if (session.editor.isDirty()) {
                player.sendActionBar(Component.text("Changes discarded", NamedTextColor.GRAY));
            }
        }
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) {
            clearRenameView(session);
            end(session);
        }
    }

    private void end(Session session) {
        sessions.remove(session.player, session);
        locks.remove(session.box, session.player);
    }

    // ---- saving (PRD §6.3) ---------------------------------------------------------------

    private void save(Player player, Session session) {
        Location location = session.box.location();
        if (location == null) {
            return;
        }
        plugin.getServer().getRegionScheduler().execute(plugin, location, () -> {
            SaveResult result = apply(location.getBlock(), session);
            player.getScheduler().run(plugin, t -> {
                if (!result.saved()) {
                    player.sendActionBar(Component.text(result.message(), NamedTextColor.RED));
                    return;
                }
                session.closingOnPurpose = true;
                end(session);
                player.closeInventory();
                if (result.message() != null) {
                    player.sendActionBar(Component.text(result.message(), NamedTextColor.YELLOW));
                }
                player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_CLOSE, 0.6f, 1.2f);
            }, null);
        });
    }

    record SaveResult(boolean saved, String message) {}

    /**
     * Region thread only. If someone has the box open, the color change is refused and
     * the name and icon still apply (PRD §5.2, §9).
     */
    private SaveResult apply(Block block, Session session) {
        if (!Tag.SHULKER_BOXES.isTagged(block.getType()) || !(block.getState() instanceof ShulkerBox box)) {
            return new SaveResult(false, "The box is gone");
        }
        var changes = session.editor.changes();
        BoxState staged = session.editor.staged();
        boolean inUse = changes.color() && !box.getInventory().getViewers().isEmpty();
        if (changes.name()) {
            box.customName(staged.name().map(Component::text).orElse(null));
        }
        if (changes.icon()) {
            var pdc = box.getPersistentDataContainer();
            StickerStore.clear(pdc);
            pdc.remove(StickerKeys.FACE);
            staged.iconId().ifPresent(id -> {
                BlockFace facing = ((Directional) block.getBlockData()).getFacing();
                Anchor anchor = Anchor.of(Faces.toCore(facing), Faces.toCore(session.clickedFace));
                StickerStore.write(pdc, new StickerData(id, session.stagedIconVisual, anchor, session.rotation));
                if (anchor == Anchor.SIDE) {
                    pdc.set(StickerKeys.FACE, PersistentDataType.STRING, session.clickedFace.name());
                }
            });
        }
        if (changes.name() || changes.icon()) {
            box.update(true, false);
        }
        if (changes.color() && !inUse) {
            BoxRecolorer.recolor(block, staged.color());
        }
        displays.refresh(block);
        return new SaveResult(true, inUse ? "Box is in use: color not changed" : null);
    }

    // ---- helpers -------------------------------------------------------------------------

    int openSessions() {
        return sessions.size();
    }

    private static ItemStack button(Material material, String name) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.itemName(Component.text(name)));
        return item;
    }

    private static Material material(BoxColor color) {
        return Objects.requireNonNull(Material.matchMaterial(color.itemId()));
    }

    private static String pretty(BoxColor color) {
        if (color == BoxColor.UNDYED) {
            return "Undyed";
        }
        List<String> words = new ArrayList<>();
        for (String w : color.dyeName().split("_")) {
            words.add(Character.toUpperCase(w.charAt(0)) + w.substring(1));
        }
        return String.join(" ", words);
    }
}
