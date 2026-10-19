package xyz.volcanobay.empiric.networking.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.volcanobay.empiric.Empiric;

/**
 * Closes a screen the server no longer lets the player view.
 */
public record UiCloseS2CPacket(byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UiCloseS2CPacket> TYPE = new CustomPacketPayload.Type<>(Empiric.id("ui_close"));
    public static final StreamCodec<ByteBuf, UiCloseS2CPacket> CODEC = ByteBufCodecs.BYTE_ARRAY.map(UiCloseS2CPacket::new, UiCloseS2CPacket::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
