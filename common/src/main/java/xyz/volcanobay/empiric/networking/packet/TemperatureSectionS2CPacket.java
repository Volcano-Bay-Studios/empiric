package xyz.volcanobay.empiric.networking.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.ChunkPos;
import xyz.volcanobay.empiric.Empiric;

public record TemperatureSectionS2CPacket(ChunkPos chunk, int section, byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TemperatureSectionS2CPacket> TYPE = new CustomPacketPayload.Type<>(Empiric.id("temperature_section"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TemperatureSectionS2CPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG.map(ChunkPos::new, ChunkPos::toLong), TemperatureSectionS2CPacket::chunk,
            ByteBufCodecs.VAR_INT, TemperatureSectionS2CPacket::section,
            ByteBufCodecs.BYTE_ARRAY, TemperatureSectionS2CPacket::data,
            TemperatureSectionS2CPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
