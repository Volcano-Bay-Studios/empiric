package xyz.volcanobay.empiric.networking.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.volcanobay.empiric.Empiric;

/**
 * Tells the server a player closed a screen, so it stops counting them as a viewer.
 */
public record UiCloseC2SPacket(byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UiCloseC2SPacket> TYPE = new CustomPacketPayload.Type<>(Empiric.id("ui_closed"));
    public static final StreamCodec<ByteBuf, UiCloseC2SPacket> CODEC = ByteBufCodecs.BYTE_ARRAY.map(UiCloseC2SPacket::new, UiCloseC2SPacket::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
