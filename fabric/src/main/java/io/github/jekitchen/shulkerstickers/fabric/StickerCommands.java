package io.github.jekitchen.shulkerstickers.fabric;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.jekitchen.shulkerstickers.core.Permissions;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.core.data.StickerCodec;
import io.github.jekitchen.shulkerstickers.core.text.InspectReport;
import io.github.jekitchen.shulkerstickers.core.text.TextKeys;
import net.minecraft.ChatFormatting;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import io.github.jekitchen.shulkerstickers.fabric.display.StickerDisplays;
import io.github.jekitchen.shulkerstickers.fabric.editor.BoxEdits;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** {@code /shulkerstickers help|toggle|inspect|clear [icon|name|all]|reload} (PRD §8). */
public final class StickerCommands {

    public enum ClearWhat { ICON, NAME, ALL }

    private StickerCommands() {
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(tree()));
    }

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return literal("shulkerstickers")
                .then(literal("help").executes(StickerCommands::help))
                .then(literal("toggle").requires(CommandSourceStack::isPlayer).executes(StickerCommands::toggle))
                .then(literal("inspect").requires(StickerCommands::isAdmin).executes(StickerCommands::inspect))
                .then(literal("clear").requires(StickerCommands::isAdmin)
                        .executes(c -> clear(c, ClearWhat.ALL))
                        .then(literal("icon").executes(c -> clear(c, ClearWhat.ICON)))
                        .then(literal("name").executes(c -> clear(c, ClearWhat.NAME)))
                        .then(literal("all").executes(c -> clear(c, ClearWhat.ALL))))
                .then(literal("reload").requires(StickerCommands::isAdmin).executes(StickerCommands::reload));
    }

    private static boolean isAdmin(CommandSourceStack source) {
        return Perms.has(source, Permissions.ADMIN);
    }

    private static int help(CommandContext<CommandSourceStack> c) {
        c.getSource().sendSuccess(() -> Text.tr(c.getSource(), TextKeys.HELP_TEXT), false);
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> c) {
        ServerPlayer player = c.getSource().getPlayer();
        boolean off = PlayerPrefs.toggleGesture(player);
        c.getSource().sendSuccess(() -> Text.tr(player, off ? TextKeys.TOGGLE_OFF : TextKeys.TOGGLE_ON), false);
        return 1;
    }

    private static int inspect(CommandContext<CommandSourceStack> c) {
        Optional<ShulkerBoxBlockEntity> box = lookedAtBox(c.getSource());
        if (box.isEmpty()) {
            c.getSource().sendFailure(Text.tr(c.getSource(), TextKeys.NO_BOX_IN_VIEW));
            return 0;
        }
        for (Component line : inspectLines(c.getSource(), box.get())) {
            c.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    /** The lines {@code inspect} prints for a box. */
    public static List<Component> inspectLines(CommandSourceStack source, ShulkerBoxBlockEntity box) {
        Map<String, Object> raw = StickerData.rawValues(box);
        BlockPos pos = box.getBlockPos();
        Optional<String> name = Optional.ofNullable(box.getCustomName()).map(io.github.jekitchen.shulkerstickers.fabric.SafeNames::plain);
        java.util.ArrayList<Component> lines = new java.util.ArrayList<>();
        for (InspectReport.Line line : InspectReport.lines(pos.getX() + " " + pos.getY() + " " + pos.getZ(),
                raw, name, StickerData.isNameHidden(box))) {
            ChatFormatting color = switch (line.tone()) {
                case HEADER -> ChatFormatting.AQUA;
                case NORMAL -> ChatFormatting.GRAY;
                case WARNING -> ChatFormatting.YELLOW;
            };
            lines.add(Text.tr(source, line.key(), line.args().toArray()).copy().withStyle(color));
        }
        return lines;
    }

    private static int clear(CommandContext<CommandSourceStack> c, ClearWhat what) {
        Optional<ShulkerBoxBlockEntity> box = lookedAtBox(c.getSource());
        if (box.isEmpty()) {
            c.getSource().sendFailure(Text.tr(c.getSource(), TextKeys.NO_BOX_IN_VIEW));
            return 0;
        }
        clear(box.get(), what);
        c.getSource().sendSuccess(() -> Text.tr(c.getSource(), TextKeys.CLEAR_DONE, what.name().toLowerCase(java.util.Locale.ROOT)), true);
        return 1;
    }

    /** Removes the icon, the name, or both from a box, keeping the face for a remaining name. */
    public static void clear(ShulkerBoxBlockEntity box, ClearWhat what) {
        Optional<Sticker> sticker = StickerData.read(box);
        boolean clearIcon = what != ClearWhat.NAME;
        boolean clearName = what != ClearWhat.ICON;
        if (clearName) {
            BoxEdits.setName(box, null);
            StickerData.setNameHidden(box, false);
        }
        boolean hasName = box.getCustomName() != null;
        Optional<Sticker> updated = sticker.map(s -> clearIcon ? new Sticker(Optional.empty(), Optional.empty(), s.anchor(), s.rot(), s.face()) : s)
                .filter(s -> s.hasIcon() || hasName);
        if (clearIcon && StickerCodec.decode(StickerData.rawValues(box)).isEmpty()) {
            // Unreadable data: clearing the icon removes it too.
            StickerData.write(box, Optional.empty());
        } else if (!updated.equals(sticker)) {
            StickerData.write(box, updated);
        }
        if (box.getLevel() instanceof ServerLevel level) {
            StickerDisplays.refresh(level, box.getBlockPos());
        }
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        List<String> warnings = Settings.load();
        for (ServerLevel level : c.getSource().getServer().getAllLevels()) {
            StickerDisplays.refreshAll(level);
        }
        c.getSource().sendSuccess(() -> warnings.isEmpty()
                ? Text.tr(c.getSource(), TextKeys.RELOAD_DONE)
                : Text.tr(c.getSource(), TextKeys.RELOAD_WARNINGS, warnings.size()), true);
        return warnings.isEmpty() ? 1 : 0;
    }

    private static Optional<ShulkerBoxBlockEntity> lookedAtBox(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return Optional.empty();
        }
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0f, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                && player.level().getBlockEntity(blockHit.getBlockPos()) instanceof ShulkerBoxBlockEntity box) {
            return Optional.of(box);
        }
        return Optional.empty();
    }
}
