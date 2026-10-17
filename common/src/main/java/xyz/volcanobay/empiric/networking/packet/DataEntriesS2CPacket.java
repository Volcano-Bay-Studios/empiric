package xyz.volcanobay.empiric.networking.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.volcanobay.empiric.Empiric;

import java.util.LinkedHashMap;
import java.util.Map;

public record DataEntriesS2CPacket(String kind, String target, Map<String, String> entries) implements CustomPacketPayload {

    public static final String ENTRIES = "entries";

    public static final CustomPacketPayload.Type<DataEntriesS2CPacket> TYPE = new CustomPacketPayload.Type<>(Empiric.id("data_entries"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DataEntriesS2CPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DataEntriesS2CPacket::kind,
            ByteBufCodecs.STRING_UTF8, DataEntriesS2CPacket::target,
            ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), DataEntriesS2CPacket::entries,
            DataEntriesS2CPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
