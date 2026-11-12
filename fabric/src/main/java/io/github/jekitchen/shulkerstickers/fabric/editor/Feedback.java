package io.github.jekitchen.shulkerstickers.fabric.editor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Editor sounds, heard only by the editing player (same sounds, volume and pitch as Paper). */
final class Feedback {

    private static final float VOLUME = 0.6f;
    private static final float PITCH = 1.2f;

    private Feedback() {
    }

    static void opened(ServerPlayer player) {
        play(player, SoundEvents.SHULKER_BOX_OPEN);
    }

    static void closed(ServerPlayer player) {
        play(player, SoundEvents.SHULKER_BOX_CLOSE);
    }

    private static void play(ServerPlayer player, SoundEvent sound) {
        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                SoundSource.BLOCKS, player.getX(), player.getY(), player.getZ(), VOLUME, PITCH, player.getRandom().nextLong()));
    }
}
