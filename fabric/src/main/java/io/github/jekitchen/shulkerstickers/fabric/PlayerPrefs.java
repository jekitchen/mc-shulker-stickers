package io.github.jekitchen.shulkerstickers.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jekitchen.shulkerstickers.core.text.TextKeys;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player state kept in player data (PRD §5.6): whether the first-placement help was shown,
 * and whether the player turned the gesture off with {@code /shulkerstickers toggle}.
 */
public final class PlayerPrefs {

    public record Prefs(boolean helpSeen, boolean gestureOff) {
        static final Prefs DEFAULT = new Prefs(false, false);
        static final Codec<Prefs> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("help_seen", false).forGetter(Prefs::helpSeen),
                Codec.BOOL.optionalFieldOf("gesture_off", false).forGetter(Prefs::gestureOff)
        ).apply(i, Prefs::new));
    }

    public static final AttachmentType<Prefs> TYPE = AttachmentRegistry.<Prefs>builder()
            .persistent(Prefs.CODEC)
            .copyOnDeath()
            .initializer(() -> Prefs.DEFAULT)
            .buildAndRegister(Identifier.fromNamespaceAndPath(ShulkerStickersFabric.MOD_ID, "player_prefs"));

    private PlayerPrefs() {
    }

    /** Loads the class so the attachment type is registered at startup. */
    static void register() {
    }

    public static Prefs get(ServerPlayer player) {
        return player.getAttachedOrElse(TYPE, Prefs.DEFAULT);
    }

    public static boolean gestureOff(ServerPlayer player) {
        return get(player).gestureOff();
    }

    /** Flips the gesture setting and returns true if the gesture is now off. */
    public static boolean toggleGesture(ServerPlayer player) {
        Prefs prefs = get(player);
        player.setAttached(TYPE, new Prefs(prefs.helpSeen(), !prefs.gestureOff()));
        return !prefs.gestureOff();
    }

    /** Shows the help message the first time a player places a shulker box, if turned on. */
    public static void onBoxPlaced(ServerPlayer player) {
        Prefs prefs = get(player);
        if (prefs.helpSeen()) {
            return;
        }
        player.setAttached(TYPE, new Prefs(true, prefs.gestureOff()));
        if (Settings.config().firstPlaceMessage()) {
            player.sendSystemMessage(Text.tr(player, TextKeys.HELP_FIRST_PLACE));
        }
    }
}
