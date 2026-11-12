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
import io.github.jekitchen.shulkerstickers.core.editor.EditorLook;
import io.github.jekitchen.shulkerstickers.paper.claims.Claims;
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
import java.util.concurrent.atomic.AtomicBoolean;
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
import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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
        /** Player's facing quadrant when "Use off-hand item" was clicked (top/bottom icons turn by it). */
        int iconRotation;
        final EditorSession editor;
        boolean renaming;
        /** Set while we close the menu on purpose (Save, or switching screens). */
        boolean closingOnPurpose;
        AnvilView renameView;
        /** The editor menu currently shown to the player, if any. */
        Inventory editorInventory;
        /** Set once Save is pressed: further presses and Esc are ignored until the save finishes. */
        volatile boolean saving;
        /**
         * Set when the box this editor opened on is broken, blown up or pushed. A box placed
         * again at the same spot is a different box, so Save must refuse (PRD §6.3, §9).
         */
        final AtomicBoolean boxGone = new AtomicBoolean();

        Session(UUID player, BlockKey box, BlockFace clickedFace, EditorSession editor) {
            this.player = player;
            this.box = box;
            this.clickedFace = clickedFace;
            this.editor = editor;
        }
    }

    private final Plugin plugin;
    private final Settings settings;
    private final DisplayManager displays;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<BlockKey, UUID> locks = new ConcurrentHashMap<>();
    private final PendingRefunds payments = new PendingRefunds();

    private final Claims claims;

    EditorService(Plugin plugin, Settings settings, DisplayManager displays) {
        this(plugin, settings, displays, Claims.none(plugin.getServer().getPluginManager(), plugin.getLogger()));
    }

    EditorService(Plugin plugin, Settings settings, DisplayManager displays, Claims claims) {
        this.plugin = plugin;
        this.settings = settings;
        this.displays = displays;
        this.claims = claims;
    }

    // ---- opening -------------------------------------------------------------------------

    /** Called on the player's (and, in reach, the box's) region thread. */
    void open(Player player, Block block, BlockFace clickedFace) {
        Optional<String> denial = Access.denial(player, block, settings.config(), plugin.getLogger(), claims);
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
            if (previous.box.equals(key)) {
                sessions.remove(previous.player, previous); // same box: keep the lock just taken
                clearRenameView(previous);
            } else {
                end(previous);
            }
        }
        BoxColor color = BoxColor.fromItemId(block.getType().getKey().toString()).orElse(BoxColor.UNDYED);
        Optional<Sticker> sticker = StickerStore.read(box.getPersistentDataContainer());
        Optional<String> name = Optional.ofNullable(box.customName())
                .map(c -> PlainTextComponentSerializer.plainText().serialize(c));
        BoxState original = new BoxState(color, sticker.flatMap(Sticker::iconId),
                sticker.flatMap(Sticker::iconVisual), name);
        Session session = new Session(player.getUniqueId(), key, clickedFace, new EditorSession(original));
        sessions.put(player.getUniqueId(), session);
        showEditor(player, session);
        if (player.getOpenInventory().getTopInventory() != session.editorInventory) {
            end(session); // another plugin cancelled the menu opening
            return;
        }
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
        ItemStack filler = new ItemStack(item(EditorLook.FILLER));
        filler.editMeta(meta -> meta.itemName(Component.text(EditorLook.FILLER_NAME)));
        for (int slot = 0; slot < EditorLayout.SIZE; slot++) {
            inventory.setItem(slot, filler.clone());
        }
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
            meta.lore(List.of(staged.iconId().map(id -> Texts.lore(player, TextKeys.EDITOR_PREVIEW_ICON, id))
                    .orElse(Texts.lore(player, TextKeys.EDITOR_PREVIEW_NO_ICON))));
        });
        inventory.setItem(EditorLayout.PREVIEW, preview);

        inventory.setItem(EditorLayout.USE_OFFHAND, offhandButton(player, config));
        inventory.setItem(EditorLayout.REMOVE_ICON,
                button(player, item(EditorLook.REMOVE_ICON), TextKeys.EDITOR_REMOVE_ICON, iconAllowed(player)));
        inventory.setItem(EditorLayout.RENAME, button(player, item(EditorLook.RENAME), TextKeys.EDITOR_RENAME, nameAllowed(player)));
        inventory.setItem(EditorLayout.CANCEL, button(player, item(EditorLook.CANCEL), TextKeys.EDITOR_CANCEL, true));

        ItemStack save = button(player, item(EditorLook.SAVE), TextKeys.EDITOR_SAVE, true);
        List<Cost> costs = Payments.costsFor(player, Costs.forChanges(session.editor.changes(), config));
        if (!costs.isEmpty()) {
            save.editMeta(meta -> meta.lore(List.of(
                    Texts.lore(player, TextKeys.EDITOR_SAVE_COST, Payments.describe(player, costs)))));
        }
        inventory.setItem(EditorLayout.SAVE, save);
    }

    private ItemStack offhandButton(Player player, StickerConfig config) {
        Material placeholder = item(EditorLook.OFFHAND_PLACEHOLDER);
        if (!iconAllowed(player)) {
            return button(player, placeholder, TextKeys.EDITOR_USE_OFFHAND, false);
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.isEmpty()) {
            return button(player, placeholder, TextKeys.EDITOR_USE_OFFHAND_EMPTY, true);
        }
        IconItems.Captured captured = IconItems.capture(offhand);
        if (!IconItems.allowed(captured.id(), captured.visual(), config)) {
            return button(player, placeholder, TextKeys.EDITOR_USE_OFFHAND_BLOCKED, true);
        }
        ItemStack shown = offhand.asOne();
        shown.editMeta(meta -> meta.itemName(Texts.label(player, TextKeys.EDITOR_USE_OFFHAND)));
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
        if (session.saving || !isButtonPress(event.getClick(), event.getRawSlot())) {
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
                            onPlayerThread(player, session, () -> openRename(player, session));
                        }
                        return;
                    }
                    case CANCEL -> {
                        closeLater(player); // the close handler discards
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
        session.iconRotation = IconOrientation.quadrant(player.getYaw());
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
        session.editor.stageName(NameRules.clamp(cleanRenameText(Objects.requireNonNullElse(text, ""))));
        onPlayerThread(player, session, () -> {
            session.closingOnPurpose = true;
            clearRenameView(session);
            showEditor(player, session);
        });
    }

    /**
     * Opening or closing menus inside an inventory click is unsafe (Bukkit javadoc), so it
     * runs on the player's next tick, if the session is still current.
     */
    private void onPlayerThread(Player player, Session session, Runnable action) {
        player.getScheduler().run(plugin, t -> {
            if (sessions.get(player.getUniqueId()) == session) {
                action.run();
            }
        }, null);
    }

    private void closeLater(Player player) {
        player.getScheduler().run(plugin, t -> player.closeInventory(), null);
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
        if (editorSession(player.getUniqueId(), event.getView()) == session && !session.saving) {
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

    /** Pays refunds owed from a Save that finished after the player left. */
    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<Cost> owed = payments.take(player.getUniqueId());
        if (!owed.isEmpty()) {
            player.getScheduler().run(plugin, t -> Payments.refund(player, owed),
                    () -> payments.owe(player.getUniqueId(), owed));
        }
    }

    /** Test hook. */
    void oweRefund(UUID player, List<Cost> costs) {
        payments.owe(player, costs);
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
            // A Save in flight is already paid for and will finish; don't report it as discarded.
            if (!session.saving && !inReach(player, session.box)) {
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

    /**
     * Player thread: check and take payment now, apply on the box's region thread, then refund
     * on the player thread whatever wasn't applied. Paying up front means nothing the player
     * does between the click and the apply (Esc, dropping the items, logging out) can make
     * the edit free. Save is one-shot: the staged state is captured now, and further presses
     * are ignored until it finishes.
     */
    private void save(Player player, Session session) {
        if (session.saving) {
            return;
        }
        Changes changes = session.editor.changes();
        if (changes.isEmpty()) {
            session.closingOnPurpose = true;
            end(session);
            closeLater(player);
            return;
        }
        Optional<String> refusal = checkSave(player, session, changes);
        if (refusal.isPresent()) {
            player.sendActionBar(Component.text(refusal.get(), NamedTextColor.RED));
            return;
        }
        Location location = session.box.location();
        if (location == null) {
            return;
        }
        session.saving = true;
        StickerConfig config = settings.config();
        boolean hideName = !player.hasPermission(Permissions.NAME_DISPLAY);
        // A new icon turns the way the player faced when it was taken; a name-only sticker
        // turns the way they face now. Only the top and bottom faces use it (see stickerAt).
        int rotation = changes.icon() && session.editor.staged().iconId().isPresent()
                ? session.iconRotation
                : IconOrientation.quadrant(player.getYaw());
        SaveRequest request = new SaveRequest(session.editor.original(), session.editor.staged(), changes,
                session.clickedFace, rotation, hideName, session.boxGone,
                List.of(player.getInventory().getItemInMainHand().clone(), player.getInventory().getItemInOffHand().clone()));
        // Creative players pay nothing, so they're refunded nothing either.
        boolean paid = !Payments.costsFor(player, Costs.forChanges(changes, config)).isEmpty();
        if (paid) {
            Payments.charge(player, Costs.forChanges(changes, config));
        }
        UUID playerId = player.getUniqueId();
        plugin.getServer().getRegionScheduler().execute(plugin, location, () -> {
            SaveResult result = apply(location.getBlock(), request);
            List<Cost> refund = paid ? Costs.forChanges(unapplied(changes, result), config) : List.of();
            Runnable gone = () -> payments.owe(playerId, refund);
            var task = player.getScheduler().run(plugin, t -> finishSave(player, session, result, refund), gone);
            if (task == null) {
                gone.run(); // the player left before the apply finished
            }
        });
    }

    /** The parts of a Save that were paid for but not applied. */
    private static Changes unapplied(Changes changes, SaveResult result) {
        if (!result.saved()) {
            return changes;
        }
        Changes applied = result.applied();
        return new Changes(changes.color() && !applied.color(), changes.icon() && !applied.icon(),
                changes.name() && !applied.name());
    }

    /** Everything that can be decided on the player thread; a translated reason to refuse. */
    private Optional<String> checkSave(Player player, Session session, Changes changes) {
        StickerConfig config = settings.config();
        BoxState staged = session.editor.staged();
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR
                || (player.getGameMode() == org.bukkit.GameMode.ADVENTURE && !config.allowAdventureMode())) {
            return Optional.of(Texts.plain(player, TextKeys.DENY_ADVENTURE));
        }
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

    /** Player thread. Refunds what wasn't applied (paid at the prices when Save was pressed). */
    private void finishSave(Player player, Session session, SaveResult result, List<Cost> refund) {
        Payments.refund(player, refund);
        // Esc during a save closes the menu but leaves the session waiting for this result.
        boolean stillOpen = editorSession(player.getUniqueId(), player.getOpenInventory()) == session;
        if (result.keepOpen() && stillOpen) {
            // e.g. the box was locked meanwhile: say why and let the player try again (PRD §6.3 step 1).
            session.saving = false;
            player.sendActionBar(Texts.text(player, NamedTextColor.RED, result.messageKey()));
            render(session.editorInventory, session, player);
            return;
        }
        session.closingOnPurpose = true;
        end(session);
        if (stillOpen) {
            player.closeInventory();
        }
        if (!result.saved()) {
            // The box was broken, moved, recolored or locked meanwhile (PRD §9).
            player.sendActionBar(Texts.text(player, NamedTextColor.RED, result.messageKey()));
            return;
        }
        if (result.messageKey() != null) {
            player.sendActionBar(Texts.text(player, NamedTextColor.YELLOW, result.messageKey()));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_CLOSE, 0.6f, 1.2f);
    }

    /** Everything a Save needs, captured on the player thread so later clicks can't change it. */
    /** {@code heldItems}: copies of the player's two hands, taken on the player's thread, for the lock check. */
    record SaveRequest(BoxState original, BoxState staged, Changes changes, BlockFace clickedFace, int rotation,
            boolean hideName, AtomicBoolean boxGone, List<ItemStack> heldItems) {
        SaveRequest {
            heldItems = List.copyOf(heldItems);
        }

        SaveRequest(BoxState original, BoxState staged, Changes changes, BlockFace clickedFace, int rotation,
                boolean hideName, AtomicBoolean boxGone) {
            this(original, staged, changes, clickedFace, rotation, hideName, boxGone, List.of());
        }
    }

    /** {@code applied} is what actually changed on the box (a refused color is left out). */
    record SaveResult(boolean saved, String messageKey, Changes applied, boolean keepOpen) {
        SaveResult(boolean saved, String messageKey, Changes applied) {
            this(saved, messageKey, applied, false);
        }
    }

    /**
     * Region thread only. Refuses if the box was broken, moved, recolored or locked since
     * the editor opened. If someone has the box open, the color change is refused and the
     * name and icon still apply (PRD §5.2, §9).
     */
    SaveResult apply(Block block, SaveRequest request) {
        BoxState original = request.original();
        Changes changes = request.changes();
        boolean hideName = request.hideName();
        if (request.boxGone().get()) {
            return new SaveResult(false, TextKeys.SAVE_BOX_GONE, NOTHING);
        }
        if (!Tag.SHULKER_BOXES.isTagged(block.getType()) || !(block.getState() instanceof ShulkerBox box)
                || !block.getType().getKey().toString().equals(original.color().itemId())) {
            return new SaveResult(false, TextKeys.SAVE_BOX_GONE, NOTHING);
        }
        if (box instanceof Lockable lockable && lockable.isLocked()
                && request.heldItems().stream().noneMatch(item -> LockKeys.unlocksWith(box, item, plugin.getLogger()))) {
            return new SaveResult(false, TextKeys.DENY_LOCKED, NOTHING, true);
        }
        BoxState staged = request.staged();
        boolean inUse = changes.color() && !box.getInventory().getViewers().isEmpty();
        var pdc = box.getPersistentDataContainer();
        BlockFace facing = ((Directional) block.getBlockData()).getFacing();

        if (changes.name()) {
            box.customName(staged.name().map(Component::text).orElse(null));
            StickerStore.setNameHidden(pdc, staged.name().isPresent() && hideName);
        }
        writeSticker(pdc, facing, request);
        box.update(true, false);
        if (changes.color() && !inUse) {
            BoxRecolorer.recolor(block, staged.color());
        }
        displays.refresh(block);
        Changes applied = new Changes(changes.color() && !inUse, changes.icon(), changes.name());
        return new SaveResult(true, inUse ? TextKeys.SAVE_BOX_IN_USE : null, applied);
    }

    /**
     * Region thread. Which sticker keys a Save writes (same rules as the Fabric build):
     * <ul>
     *   <li>a new icon: a sticker on the clicked face;</li>
     *   <li>icon removed from a named box: the name stays where it was drawn;</li>
     *   <li>icon removed, no name: the sticker keys go;</li>
     *   <li>a name given to a box with no sticker keys: a name-only sticker on the clicked face;</li>
     *   <li>the name removed from a name-only box: its sticker keys go.</li>
     * </ul>
     * Data this version can't read is only replaced when the player changed the icon.
     */
    private static void writeSticker(org.bukkit.persistence.PersistentDataContainer pdc, BlockFace facing,
            SaveRequest request) {
        Changes changes = request.changes();
        BoxState staged = request.staged();
        Optional<Sticker> current = StickerStore.read(pdc);
        boolean hasStickerKeys = !StickerStore.raw(pdc).isEmpty();
        if (changes.icon()) {
            if (staged.iconId().isPresent()) {
                StickerStore.write(pdc, stickerAt(facing, request, staged.iconId(), staged.iconVisual()));
            } else if (staged.name().isPresent()) {
                StickerStore.write(pdc, current
                        .map(s -> new Sticker(Optional.empty(), Optional.empty(), s.anchor(), s.rot(), s.face()))
                        .orElseGet(() -> stickerAt(facing, request, Optional.empty(), Optional.empty())));
            } else {
                clearStickerKeys(pdc);
            }
        } else if (changes.name()) {
            if (staged.name().isPresent() && !hasStickerKeys) {
                StickerStore.write(pdc, stickerAt(facing, request, Optional.empty(), Optional.empty()));
            } else if (staged.name().isEmpty() && current.isPresent() && !current.get().hasIcon()) {
                clearStickerKeys(pdc);
            }
        }
    }

    private static void clearStickerKeys(org.bukkit.persistence.PersistentDataContainer pdc) {
        boolean hidden = StickerStore.isNameHidden(pdc);
        StickerStore.clear(pdc);
        StickerStore.setNameHidden(pdc, hidden);
    }

    private static final Changes NOTHING = new Changes(false, false, false);

    private static Sticker stickerAt(BlockFace facing, SaveRequest request, Optional<String> iconId,
            Optional<String> visual) {
        Anchor anchor = Anchor.of(Faces.toCore(facing), Faces.toCore(request.clickedFace()));
        // rot only turns content on the top and bottom faces (PRD §6.1); elsewhere it is 0.
        boolean topOrBottom = request.clickedFace() == BlockFace.UP || request.clickedFace() == BlockFace.DOWN;
        return new Sticker(iconId, visual, anchor, topOrBottom ? request.rotation() : 0,
                anchor == Anchor.SIDE ? Optional.of(Faces.toCore(request.clickedFace())) : Optional.empty());
    }

    // ---- helpers -------------------------------------------------------------------------

    /**
     * Plugin disable: close every editor and rename screen so the menu can't turn into a
     * plain chest once our listeners are gone, then release every lock. On Folia/Canvas a
     * plugin is only disabled at shutdown and players are owned by their regions, so a
     * player not owned by this thread is skipped; their client is disconnected anyway.
     */
    void shutdown() {
        for (Session session : List.copyOf(sessions.values())) {
            Player player = plugin.getServer().getPlayer(session.player);
            clearRenameView(session);
            session.closingOnPurpose = true;
            end(session);
            if (player != null && plugin.getServer().isOwnedByCurrentRegion(player.getLocation())) {
                player.closeInventory();
            }
        }
        sessions.clear();
        locks.clear();
    }

    /**
     * The same filter vanilla applies to anvil text ({@code StringUtil.filterText}): drops the
     * formatting sign and control characters. Applied again here so a name can never carry
     * formatting codes, whatever the server or client sends.
     */
    static String cleanRenameText(String text) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints()
                .filter(c -> c != '\u00a7' && c >= ' ' && c != 0x7f)
                .forEach(out::appendCodePoint);
        return out.toString();
    }

    /** False when color, icon and name editing are all switched off: the gesture is then vanilla. */
    boolean editingEnabled() {
        StickerConfig config = settings.config();
        return config.colorEnabled() || config.iconEnabled() || config.nameEditEnabled();
    }

    // ---- the box going away during an edit -----------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        boxRemoved(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onDestroy(BlockDestroyEvent event) {
        boxRemoved(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::boxRemoved);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::boxRemoved);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onPistonExtend(BlockPistonExtendEvent event) {
        event.getBlocks().forEach(this::boxRemoved);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onPistonRetract(BlockPistonRetractEvent event) {
        event.getBlocks().forEach(this::boxRemoved);
    }

    /**
     * The box's region thread. Marks the editing session's box as gone, so a pending or later
     * Save refuses, and closes that player's editor with the reason (PRD §9). Commands such as
     * {@code /setblock} fire no event; for those the position-and-color check at Save remains.
     */
    private void boxRemoved(Block block) {
        UUID holder = locks.get(BlockKey.of(block));
        Session session = holder == null ? null : sessions.get(holder);
        if (session == null || !session.box.equals(BlockKey.of(block))) {
            return;
        }
        session.boxGone.set(true);
        Player player = plugin.getServer().getPlayer(holder);
        if (player == null) {
            return;
        }
        player.getScheduler().run(plugin, t -> {
            if (sessions.get(player.getUniqueId()) != session || session.saving) {
                return; // a running save reports the refusal itself
            }
            clearRenameView(session);
            session.closingOnPurpose = true;
            end(session);
            player.closeInventory();
            player.sendActionBar(Texts.text(player, NamedTextColor.RED, TextKeys.SAVE_BOX_GONE));
        }, null);
    }

    int openSessions() {
        return sessions.size();
    }

    Optional<UUID> editorOf(Block block) {
        return Optional.ofNullable(locks.get(BlockKey.of(block)));
    }

    /** An unavailable button keeps its own item and gets the "disabled" lore line (owner decision 11). */
    private static ItemStack button(Player player, Material material, String key, boolean enabled) {
        ItemStack item = new ItemStack(material);
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

    private static Material item(String id) {
        return Objects.requireNonNull(Material.matchMaterial(id), id);
    }

    private static Material material(BoxColor color) {
        return Objects.requireNonNull(Material.matchMaterial(color.itemId()));
    }
}
