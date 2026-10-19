package xyz.volcanobay.empiric.networking.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.volcanobay.empiric.Empiric;

/**
 * Opens a screen on the client, carrying the screen id and the module's locator.
 */
public record UiOpenS2CPacket(byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UiOpenS2CPacket> TYPE = new CustomPacketPayload.Type<>(Empiric.id("ui_open"));
    public static final StreamCodec<ByteBuf, UiOpenS2CPacket> CODEC = ByteBufCodecs.BYTE_ARRAY.map(UiOpenS2CPacket::new, UiOpenS2CPacket::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
