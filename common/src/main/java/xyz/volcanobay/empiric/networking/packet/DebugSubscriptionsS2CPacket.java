package xyz.volcanobay.empiric.networking.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.volcanobay.empiric.Empiric;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DebugSubscriptionsS2CPacket(Map<String, List<String>> subscriptions, Map<String, List<String>> suppressed) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DebugSubscriptionsS2CPacket> TYPE = new CustomPacketPayload.Type<>(Empiric.id("debug_subscriptions"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DebugSubscriptionsS2CPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())), DebugSubscriptionsS2CPacket::subscriptions,
            ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())), DebugSubscriptionsS2CPacket::suppressed,
            DebugSubscriptionsS2CPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
