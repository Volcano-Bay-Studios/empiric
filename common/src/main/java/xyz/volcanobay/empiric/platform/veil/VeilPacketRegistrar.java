package xyz.volcanobay.empiric.platform.veil;

import foundry.veil.api.network.VeilPacketManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import xyz.volcanobay.empiric.client.ClientLookup;
import xyz.volcanobay.empiric.platform.PacketRegistrar;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class VeilPacketRegistrar implements PacketRegistrar {

    private final VeilPacketManager manager;

    public VeilPacketRegistrar(String modId, String version) {
        this.manager = VeilPacketManager.create(modId, version);
    }

    @Override
    public <T extends CustomPacketPayload> void serverbound(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
        manager.registerServerbound(type, codec, (packet, context) -> {
            ServerPlayer player = context.player();
            if (player == null) {
                return;
            }
            if (player.server.isSameThread()) {
                handler.accept(packet, player);
            } else {
                player.server.execute(() -> handler.accept(packet, player));
            }
        });
    }

    @Override
    public <T extends CustomPacketPayload> void clientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Consumer<T> handler) {
        manager.registerClientbound(type, codec, (packet, context) -> ClientLookup.execute(() -> handler.accept(packet)));
    }
}
