package io.github.jekitchen.shulkerstickers.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.jekitchen.shulkerstickers.core.Permissions;
import io.github.jekitchen.shulkerstickers.core.text.TextKeys;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * {@code /shulkerstickers help|toggle|inspect|clear [icon|name|all]|reload} (PRD §8).
 * Box commands read the target on the player's thread, then do the work on the box's region.
 */
final class StickerCommands {

    enum ClearPart { ICON, NAME, ALL }

    private static final int TARGET_RANGE = 6;

    private final Plugin plugin;
    private final Settings settings;
    private final DisplayManager displays;

    StickerCommands(Plugin plugin, Settings settings, DisplayManager displays) {
        this.plugin = plugin;
        this.settings = settings;
        this.displays = displays;
    }

    LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("shulkerstickers")
                .then(Commands.literal("help").executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    sender.sendMessage(Texts.text(sender, NamedTextColor.AQUA, TextKeys.HELP_TEXT));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("toggle")
                        .requires(src -> src.getSender() instanceof Player)
                        .executes(ctx -> {
                            toggle((Player) ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("inspect")
                        .requires(src -> src.getSender() instanceof Player && src.getSender().hasPermission(Permissions.ADMIN))
                        .executes(ctx -> {
                            inspect((Player) ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("clear")
                        .requires(src -> src.getSender() instanceof Player && src.getSender().hasPermission(Permissions.ADMIN))
                        .executes(ctx -> clear((Player) ctx.getSource().getSender(), ClearPart.ALL))
                        .then(Commands.literal("icon").executes(ctx -> clear((Player) ctx.getSource().getSender(), ClearPart.ICON)))
                        .then(Commands.literal("name").executes(ctx -> clear((Player) ctx.getSource().getSender(), ClearPart.NAME)))
                        .then(Commands.literal("all").executes(ctx -> clear((Player) ctx.getSource().getSender(), ClearPart.ALL))))
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission(Permissions.ADMIN))
                        .executes(ctx -> {
                            reload(ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    static void toggle(Player player) {
        boolean off = !GestureListener.gestureOff(player);
        player.getPersistentDataContainer().set(PaperKeys.GESTURE_OFF, PersistentDataType.BOOLEAN, off);
        player.sendMessage(Texts.text(player, NamedTextColor.GRAY, off ? TextKeys.TOGGLE_OFF : TextKeys.TOGGLE_ON));
    }

    private void reload(CommandSender sender) {
        List<String> warnings = settings.reload();
        displays.refreshAllLater();
        sender.sendMessage(warnings.isEmpty()
                ? Texts.text(sender, NamedTextColor.GREEN, TextKeys.RELOAD_DONE)
                : Texts.text(sender, NamedTextColor.YELLOW, TextKeys.RELOAD_WARNINGS, warnings.size()));
    }

    private Block target(Player player) {
        Block block = player.getTargetBlockExact(TARGET_RANGE);
        if (block == null || !Tag.SHULKER_BOXES.isTagged(block.getType())) {
            player.sendMessage(Texts.text(player, NamedTextColor.RED, TextKeys.NO_BOX_IN_VIEW));
            return null;
        }
        return block;
    }

    private void inspect(Player player) {
        Block block = target(player);
        if (block == null) {
            return;
        }
        plugin.getServer().getRegionScheduler().execute(plugin, block.getLocation(), () -> {
            if (!(block.getState(false) instanceof ShulkerBox box)) {
                return;
            }
            var pdc = box.getPersistentDataContainer();
            Map<String, Object> raw = StickerStore.raw(pdc);
            boolean readable = StickerStore.read(pdc).isPresent();
            boolean hidden = StickerStore.isNameHidden(pdc);
            String where = block.getX() + " " + block.getY() + " " + block.getZ();
            player.getScheduler().run(plugin, t -> {
                player.sendMessage(Texts.text(player, NamedTextColor.AQUA, TextKeys.INSPECT_HEADER, where));
                if (raw.isEmpty() && !hidden) {
                    player.sendMessage(Texts.text(player, NamedTextColor.GRAY, TextKeys.INSPECT_NONE));
                    return;
                }
                if (!raw.isEmpty() && !readable) {
                    player.sendMessage(Texts.text(player, NamedTextColor.YELLOW, TextKeys.INSPECT_UNREADABLE));
                }
                raw.forEach((k, v) -> player.sendMessage(Texts.text(player, NamedTextColor.GRAY, TextKeys.INSPECT_LINE, k, v)));
                if (hidden) {
                    player.sendMessage(Texts.text(player, NamedTextColor.GRAY, TextKeys.INSPECT_LINE, "name_hidden", 1));
                }
            }, null);
        });
    }

    private int clear(Player player, ClearPart part) {
        Block block = target(player);
        if (block == null) {
            return 0;
        }
        plugin.getServer().getRegionScheduler().execute(plugin, block.getLocation(), () -> {
            if (!(block.getState() instanceof ShulkerBox box)) {
                return;
            }
            clearBox(box, part);
            displays.refresh(block);
            player.getScheduler().run(plugin, t -> player.sendMessage(
                    Texts.text(player, NamedTextColor.GREEN, TextKeys.CLEAR_DONE, part.name().toLowerCase(java.util.Locale.ROOT))),
                    null);
        });
        return Command.SINGLE_SUCCESS;
    }

    /** Region thread. */
    static void clearBox(ShulkerBox box, ClearPart part) {
        var pdc = box.getPersistentDataContainer();
        if (part == ClearPart.NAME || part == ClearPart.ALL) {
            box.customName(null);
            StickerStore.setNameHidden(pdc, false);
            // A name-only sticker only said where the name goes; without the name it goes too.
            if (StickerStore.read(pdc).filter(s -> !s.hasIcon()).isPresent()) {
                StickerStore.clear(pdc);
            }
        }
        if (part == ClearPart.ICON || part == ClearPart.ALL) {
            var sticker = StickerStore.read(pdc);
            boolean hidden = StickerStore.isNameHidden(pdc);
            StickerStore.clear(pdc);
            StickerStore.setNameHidden(pdc, hidden);
            // Keep where the name is drawn if only the icon goes.
            sticker.filter(s -> part == ClearPart.ICON && box.customName() != null)
                    .ifPresent(s -> StickerStore.write(pdc, new io.github.jekitchen.shulkerstickers.core.data.Sticker(
                            java.util.Optional.empty(), java.util.Optional.empty(), s.anchor(), s.rot(), s.face())));
        }
        box.update(true, false);
    }
}
