package xyz.volcanobay.empiric.platform;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Registers Empiric's packets with the loader. Handlers always run on the receiving side's main thread.
 */
public interface PacketRegistrar {

    <T extends CustomPacketPayload> void serverbound(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler);

    <T extends CustomPacketPayload> void clientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Consumer<T> handler);
}
