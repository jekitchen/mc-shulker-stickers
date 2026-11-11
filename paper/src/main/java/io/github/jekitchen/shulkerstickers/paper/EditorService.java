package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.BoxState;
import io.github.jekitchen.shulkerstickers.core.Changes;
import io.github.jekitchen.shulkerstickers.core.EditorSession;
import io.github.jekitchen.shulkerstickers.core.Permissions;
import io.github.jekitchen.shulkerstickers.core.config.Cost;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout;
import io.github.jekitchen.shulkerstickers.core.rules.Costs;
import io.github.jekitchen.shulkerstickers.core.rules.NameRules;
import io.github.jekitchen.shulkerstickers.core.text.TextKeys;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Lockable;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.Plugin;

/**
 * The Sticker editor (PRD §5.2) and its rename screen (§5.4).
 *
 * <p>Threading (Folia/Canvas): inventory events arrive on the player's region thread, so
 * menus, costs and messages are handled there or through the player's entity scheduler.
 * Reading and changing the box always goes through the region scheduler for the box.
 */
final class EditorService implements Listener {

    /** One player's open editor. Fields are only touched from that player's thread. */
    static final class Session {
        final UUID player;
        final BlockKey box;
        final BlockFace clickedFace;
        final int rotation;
        final EditorSession editor;
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
    private final Settings settings;
    private final DisplayManager displays;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<BlockKey, UUID> locks = new ConcurrentHashMap<>();

    EditorService(Plugin plugin, Settings settings, DisplayManager displays) {
        this.plugin = plugin;
        this.settings = settings;
        this.displays = displays;
    }

    // ---- opening -------------------------------------------------------------------------

    /** Called on the player's (and, in reach, the box's) region thread. */
    void open(Player player, Block block, BlockFace clickedFace) {
        Optional<String> denial = Access.denial(player, block, settings.config());
        if (denial.isPresent()) {
            player.sendActionBar(Texts.text(player, NamedTextColor.RED, denial.get()));
            return;
        }
        if (!(block.getState(false) instanceof ShulkerBox box)) {
            return;
        }
        BlockKey key = BlockKey.of(block);
        UUID holder = locks.putIfAbsent(key, player.getUniqueId());
        if (holder != null && !holder.equals(player.getUniqueId())) {
            Player other = plugin.getServer().getPlayer(holder);
            player.sendActionBar(Texts.text(player, NamedTextColor.RED, TextKeys.DENY_IN_USE_EDITOR,
                    other == null ? "?" : other.getName()));
            return;
        }
        Session previous = sessions.get(player.getUniqueId());
        if (previous != null) {
            end(previous);
        }
        BoxColor color = BoxColor.fromItemId(block.getType().getKey().toString()).orElse(BoxColor.UNDYED);
        Optional<Sticker> sticker = StickerStore.read(box.getPersistentDataContainer());
        Optional<String> name = Optional.ofNullable(box.customName())
                .map(c -> PlainTextComponentSerializer.plainText().serialize(c));
        int rotation = Math.floorMod(Math.round(player.getYaw() / 90f), 4);
        BoxState original = new BoxState(color, sticker.flatMap(Sticker::iconId),
                sticker.flatMap(Sticker::iconVisual), name);
        Session session = new Session(player.getUniqueId(), key, clickedFace, rotation, new EditorSession(original));
        sessions.put(player.getUniqueId(), session);
        showEditor(player, session);
        watchReach(player, session);
        player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 0.6f, 1.2f);
    }

    private void showEditor(Player player, Session session) {
        EditorHolder holder = new EditorHolder(session);
        String title = session.editor.staged().name().orElse(Texts.plain(player, TextKeys.EDITOR_TITLE_UNNAMED));
        Inventory inventory = plugin.getServer().createInventory(holder, EditorLayout.SIZE,
                Texts.text(player, TextKeys.EDITOR_TITLE, title));
        holder.inventory(inventory);
        session.editorInventory = inventory;
        render(inventory, session, player);
        session.renaming = false;
        player.openInventory(inventory);
        session.closingOnPurpose = false;
    }

    // ---- feature switches ----------------------------------------------------------------

    private boolean colorAllowed(Player player) {
        return settings.config().colorEnabled() && player.hasPermission(Permissions.COLOR);
    }

    private boolean iconAllowed(Player player) {
        return settings.config().iconEnabled() && player.hasPermission(Permissions.ICON);
    }

    private boolean nameAllowed(Player player) {
        return settings.config().nameEditEnabled() && player.hasPermission(Permissions.NAME);
    }

    // ---- rendering -----------------------------------------------------------------------

    private void render(Inventory inventory, Session session, Player player) {
        StickerConfig config = settings.config();
        BoxState staged = session.editor.staged();
        boolean colorOn = colorAllowed(player);
        for (BoxColor color : BoxColor.values()) {
            ItemStack item = new ItemStack(material(color));
            item.editMeta(meta -> {
                meta.itemName(Texts.label(player, TextKeys.color(color)));
                meta.setEnchantmentGlintOverride(color == staged.color());
                if (!colorOn) {
                    meta.lore(List.of(Texts.lore(player, TextKeys.EDITOR_FEATURE_DISABLED)));
                }
            });
            inventory.setItem(color.ordinal(), item);
        }

        ItemStack preview = new ItemStack(material(staged.color()));
        preview.editMeta(meta -> {
            meta.itemName(staged.name().map(n -> (Component) Component.text(n))
                    .orElse(Texts.label(player, TextKeys.EDITOR_TITLE_UNNAMED)));
            meta.lore(List.of(Texts.lore(player, TextKeys.EDITOR_PREVIEW),
                    staged.iconId().map(id -> Texts.lore(player, TextKeys.EDITOR_PREVIEW_ICON, id))
                            .orElse(Texts.lore(player, TextKeys.EDITOR_PREVIEW_NO_ICON))));
        });
        inventory.setItem(EditorLayout.PREVIEW, preview);

        inventory.setItem(EditorLayout.USE_OFFHAND, offhandButton(player, config));
        inventory.setItem(EditorLayout.REMOVE_ICON,
                button(player, Material.BARRIER, TextKeys.EDITOR_REMOVE_ICON, iconAllowed(player)));
        inventory.setItem(EditorLayout.RENAME, button(player, Material.NAME_TAG, TextKeys.EDITOR_RENAME, nameAllowed(player)));
        inventory.setItem(EditorLayout.CANCEL, button(player, Material.RED_CONCRETE, TextKeys.EDITOR_CANCEL, true));

        ItemStack save = button(player, Material.LIME_CONCRETE, TextKeys.EDITOR_SAVE, true);
        List<Cost> costs = Payments.costsFor(player, Costs.forChanges(session.editor.changes(), config));
        if (!costs.isEmpty()) {
            save.editMeta(meta -> meta.lore(List.of(
                    Texts.lore(player, TextKeys.EDITOR_SAVE_COST, Payments.describe(player, costs)))));
        }
        inventory.setItem(EditorLayout.SAVE, save);
    }

    private ItemStack offhandButton(Player player, StickerConfig config) {
        if (!iconAllowed(player)) {
            return button(player, Material.GRAY_STAINED_GLASS_PANE, TextKeys.EDITOR_USE_OFFHAND, false);
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.isEmpty()) {
            return button(player, Material.GRAY_STAINED_GLASS_PANE, TextKeys.EDITOR_USE_OFFHAND_EMPTY, true);
        }
        IconItems.Captured captured = IconItems.capture(offhand);
        boolean ok = IconItems.allowed(captured.id(), captured.visual(), config);
        ItemStack shown = offhand.asOne();
        shown.editMeta(meta -> meta.itemName(Texts.label(player,
                ok ? TextKeys.EDITOR_USE_OFFHAND : TextKeys.EDITOR_USE_OFFHAND_BLOCKED)));
        return shown;
    }

    // ---- editor clicks -------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    void onClick(InventoryClickEvent event) {
        if (event.getView() instanceof AnvilView anvil && isRenameView(event.getWhoClicked().getUniqueId(), anvil)) {
            event.setCancelled(true);
            if (event.getRawSlot() == 2 && (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)) {
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
        if (!isButtonPress(event.getClick(), event.getRawSlot())) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        EditorLayout.buttonAt(event.getRawSlot()).ifPresent(button -> press(player, session, button));
    }

    /**
     * Only a plain left or right click on one of the editor's own slots counts. Shift,
     * number keys, off-hand swap, double-click, drops and clicks in the player's inventory
     * or outside the window are all rejected (AC-12).
     */
    static boolean isButtonPress(ClickType type, int rawSlot) {
        return (type == ClickType.LEFT || type == ClickType.RIGHT) && EditorLayout.isMenuSlot(rawSlot);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onDrag(InventoryDragEvent event) {
        if (editorSession(event.getWhoClicked().getUniqueId(), event.getView()) != null
                || (event.getView() instanceof AnvilView anvil
                        && isRenameView(event.getWhoClicked().getUniqueId(), anvil))) {
            event.setCancelled(true);
        }
    }

    /** The session whose editor menu is the top of {@code view}. */
    private Session editorSession(UUID player, org.bukkit.inventory.InventoryView view) {
        Session session = sessions.get(player);
        return session != null && session.editorInventory != null
                && session.editorInventory.equals(view.getTopInventory()) ? session : null;
    }

    private void press(Player player, Session session, EditorLayout.Button button) {
        switch (button) {
            case EditorLayout.Button.Color c -> {
                if (colorAllowed(player)) {
                    session.editor.stageColor(c.color());
                }
            }
            case EditorLayout.Button.Action action -> {
                switch (action) {
                    case USE_OFFHAND -> stageOffhand(player, session);
                    case REMOVE_ICON -> {
                        if (iconAllowed(player)) {
                            session.editor.removeIcon();
                        }
                    }
                    case RENAME -> {
                        if (nameAllowed(player)) {
                            openRename(player, session);
                        }
                        return;
                    }
                    case CANCEL -> {
                        player.closeInventory(); // the close handler discards
                        return;
                    }
                    case SAVE -> {
                        save(player, session);
                        return;
                    }
                    case PREVIEW -> {
                        return;
                    }
                }
            }
        }
        render(player.getOpenInventory().getTopInventory(), session, player);
    }

    /** The icon is read from the off-hand only now, on this click (PRD §5.3). */
    private void stageOffhand(Player player, Session session) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (!iconAllowed(player) || offhand.isEmpty()) {
            return;
        }
        IconItems.Captured captured = IconItems.capture(offhand);
        if (!IconItems.allowed(captured.id(), captured.visual(), settings.config())) {
            player.sendActionBar(Texts.text(player, NamedTextColor.RED, TextKeys.SAVE_ICON_BLOCKED));
            return;
        }
        session.editor.stageIcon(captured.id(), captured.visual());
    }

    // ---- rename screen -------------------------------------------------------------------

    private void openRename(Player player, Session session) {
        session.closingOnPurpose = true;
        session.renaming = true;
        AnvilView view = MenuType.ANVIL.builder()
                .title(Texts.text(player, TextKeys.RENAME_TITLE))
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
        session.editor.stageName(NameRules.clamp(Objects.requireNonNullElse(text, "")));
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
            discard(player, session);
        }
    }

    /** Esc, Cancel, walking away: nothing changes and nothing is charged (PRD §5.2, AC-11). */
    private void discard(Player player, Session session) {
        end(session);
        if (session.editor.isDirty()) {
            player.sendActionBar(Texts.text(player, NamedTextColor.GRAY, TextKeys.EDITOR_DISCARDED));
        }
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        endFor(event.getPlayer(), false);
    }

    @EventHandler
    void onDeath(PlayerDeathEvent event) {
        endFor(event.getPlayer(), true);
    }

    @EventHandler
    void onWorldChange(PlayerChangedWorldEvent event) {
        endFor(event.getPlayer(), true);
    }

    private void endFor(Player player, boolean closeMenu) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        clearRenameView(session);
        end(session);
        if (closeMenu) {
            session.closingOnPurpose = true;
            player.closeInventory();
        }
    }

    private void end(Session session) {
        sessions.remove(session.player, session);
        locks.remove(session.box, session.player);
        // The reach watcher stops itself on its next run once the session is gone.
    }

    /** Closes (and discards) the editor once the player is out of reach of the box. */
    private void watchReach(Player player, Session session) {
        player.getScheduler().runAtFixedRate(plugin, task -> {
            if (sessions.get(player.getUniqueId()) != session) {
                cancelQuietly(task);
                return;
            }
            if (!inReach(player, session.box)) {
                clearRenameView(session);
                session.closingOnPurpose = true;
                player.closeInventory();
                discard(player, session);
            }
        }, null, 10, 10);
    }

    private static void cancelQuietly(ScheduledTask task) {
        try {
            task.cancel();
        } catch (UnsupportedOperationException e) {
            // MockBukkit doesn't implement cancel; real servers do.
        }
    }

    static boolean inReach(Player player, BlockKey box) {
        if (!player.getWorld().getUID().equals(box.world())) {
            return false;
        }
        AttributeInstance range = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        double reach = (range == null ? 4.5 : range.getValue()) + 1.0;
        Location eye = player.getEyeLocation();
        double dx = eye.getX() - (box.x() + 0.5);
        double dy = eye.getY() - (box.y() + 0.5);
        double dz = eye.getZ() - (box.z() + 0.5);
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    // ---- saving (PRD §6.3) ---------------------------------------------------------------

    /** Player thread: check, charge, then apply on the box's region thread. */
    private void save(Player player, Session session) {
        Changes changes = session.editor.changes();
        if (changes.isEmpty()) {
            session.closingOnPurpose = true;
            end(session);
            player.closeInventory();
            return;
        }
        Optional<String> refusal = checkSave(player, session, changes);
        if (refusal.isPresent()) {
            player.sendActionBar(Component.text(refusal.get(), NamedTextColor.RED));
            return;
        }
        List<Cost> costs = Payments.costsFor(player, Costs.forChanges(changes, settings.config()));
        Payments.charge(player, costs);
        boolean hideName = !player.hasPermission(Permissions.NAME_DISPLAY);
        Location location = session.box.location();
        if (location == null) {
            Payments.refund(player, costs);
            return;
        }
        plugin.getServer().getRegionScheduler().execute(plugin, location, () -> {
            SaveResult result = apply(location.getBlock(), session, changes, hideName);
            player.getScheduler().run(plugin, t -> finishSave(player, session, costs, result), null);
        });
    }

    /** Everything that can be decided on the player thread; a translated reason to refuse. */
    private Optional<String> checkSave(Player player, Session session, Changes changes) {
        StickerConfig config = settings.config();
        BoxState staged = session.editor.staged();
        if ((changes.color() && !colorAllowed(player)) || (changes.icon() && !iconAllowed(player))
                || (changes.name() && !nameAllowed(player))) {
            return Optional.of(Texts.plain(player, TextKeys.DENY_PERMISSION));
        }
        if (changes.name() && staged.name().flatMap(n -> settings.blockedWords().firstMatch(n)).isPresent()) {
            return Optional.of(Texts.plain(player, TextKeys.SAVE_BLOCKED_WORD));
        }
        if (changes.icon() && staged.iconId().isPresent()
                && !IconItems.allowed(staged.iconId().get(), staged.iconVisual(), config)) {
            return Optional.of(Texts.plain(player, TextKeys.SAVE_ICON_BLOCKED));
        }
        List<Cost> costs = Payments.costsFor(player, Costs.forChanges(changes, config));
        if (!Payments.canPay(player, costs)) {
            return Optional.of(Texts.plain(player, TextKeys.SAVE_CANNOT_PAY, Payments.describe(player, costs)));
        }
        return Optional.empty();
    }

    private void finishSave(Player player, Session session, List<Cost> costs, SaveResult result) {
        session.closingOnPurpose = true;
        end(session);
        player.closeInventory();
        if (!result.saved()) {
            Payments.refund(player, costs);
            player.sendActionBar(Texts.text(player, NamedTextColor.RED, result.messageKey()));
            return;
        }
        if (result.messageKey() != null) {
            player.sendActionBar(Texts.text(player, NamedTextColor.YELLOW, result.messageKey()));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_CLOSE, 0.6f, 1.2f);
    }

    record SaveResult(boolean saved, String messageKey) {}

    /**
     * Region thread only. Refuses if the box was broken, moved, recolored or locked since
     * the editor opened. If someone has the box open, the color change is refused and the
     * name and icon still apply (PRD §5.2, §9).
     */
    SaveResult apply(Block block, Session session, Changes changes, boolean hideName) {
        BoxState original = session.editor.original();
        if (!Tag.SHULKER_BOXES.isTagged(block.getType()) || !(block.getState() instanceof ShulkerBox box)
                || !block.getType().getKey().toString().equals(original.color().itemId())) {
            return new SaveResult(false, TextKeys.SAVE_BOX_GONE);
        }
        if (box instanceof Lockable lockable && lockable.isLocked()) {
            return new SaveResult(false, TextKeys.DENY_LOCKED);
        }
        BoxState staged = session.editor.staged();
        boolean inUse = changes.color() && !box.getInventory().getViewers().isEmpty();
        var pdc = box.getPersistentDataContainer();
        BlockFace facing = ((Directional) block.getBlockData()).getFacing();
        Optional<Sticker> current = StickerStore.read(pdc);

        if (changes.name()) {
            box.customName(staged.name().map(Component::text).orElse(null));
            StickerStore.setNameHidden(pdc, staged.name().isPresent() && hideName);
        }
        if (changes.icon() || (changes.name() && staged.name().isPresent() && current.isEmpty())) {
            if (staged.iconId().isPresent() || staged.name().isPresent()) {
                // A name-only sticker records the clicked face so the name has a face to go on.
                StickerStore.write(pdc, stickerAt(facing, session, staged.iconId(), staged.iconVisual()));
            } else {
                clearStickerKeys(pdc);
            }
        }
        box.update(true, false);
        if (changes.color() && !inUse) {
            BoxRecolorer.recolor(block, staged.color());
        }
        displays.refresh(block);
        return new SaveResult(true, inUse ? TextKeys.SAVE_BOX_IN_USE : null);
    }

    private static void clearStickerKeys(org.bukkit.persistence.PersistentDataContainer pdc) {
        boolean hidden = StickerStore.isNameHidden(pdc);
        StickerStore.clear(pdc);
        StickerStore.setNameHidden(pdc, hidden);
    }

    private static Sticker stickerAt(BlockFace facing, Session session, Optional<String> iconId,
            Optional<String> visual) {
        Anchor anchor = Anchor.of(Faces.toCore(facing), Faces.toCore(session.clickedFace));
        return new Sticker(iconId, visual, anchor, session.rotation,
                anchor == Anchor.SIDE ? Optional.of(Faces.toCore(session.clickedFace)) : Optional.empty());
    }

    // ---- helpers -------------------------------------------------------------------------

    int openSessions() {
        return sessions.size();
    }

    Optional<UUID> editorOf(Block block) {
        return Optional.ofNullable(locks.get(BlockKey.of(block)));
    }

    private static ItemStack button(Player player, Material material, String key, boolean enabled) {
        ItemStack item = new ItemStack(enabled ? material : Material.GRAY_STAINED_GLASS_PANE);
        item.editMeta(meta -> {
            meta.itemName(Texts.label(player, key));
            if (!enabled) {
                List<Component> lore = new ArrayList<>();
                lore.add(Texts.lore(player, TextKeys.EDITOR_FEATURE_DISABLED));
                meta.lore(lore);
            }
        });
        return item;
    }

    private static Material material(BoxColor color) {
        return Objects.requireNonNull(Material.matchMaterial(color.itemId()));
    }
}
