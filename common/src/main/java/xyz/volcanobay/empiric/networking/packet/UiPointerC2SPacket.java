package xyz.volcanobay.empiric.networking.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.volcanobay.empiric.Empiric;

/**
 * Where a player's pointer is on a screen with presence, relative to the screen's content. NaN means it left.
 */
public record UiPointerC2SPacket(byte[] data, float x, float y) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UiPointerC2SPacket> TYPE = new CustomPacketPayload.Type<>(Empiric.id("ui_pointer"));
    public static final StreamCodec<ByteBuf, UiPointerC2SPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE_ARRAY, UiPointerC2SPacket::data,
            ByteBufCodecs.FLOAT, UiPointerC2SPacket::x,
            ByteBufCodecs.FLOAT, UiPointerC2SPacket::y,
            UiPointerC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
