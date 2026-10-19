package xyz.volcanobay.empiric.networking.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.volcanobay.empiric.Empiric;

/**
 * Another player's pointer on a screen with presence. NaN means it left.
 */
public record UiPointerS2CPacket(byte[] data, int player, String name, float x, float y) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UiPointerS2CPacket> TYPE = new CustomPacketPayload.Type<>(Empiric.id("ui_pointer_relay"));
    public static final StreamCodec<ByteBuf, UiPointerS2CPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE_ARRAY, UiPointerS2CPacket::data,
            ByteBufCodecs.VAR_INT, UiPointerS2CPacket::player,
            ByteBufCodecs.STRING_UTF8, UiPointerS2CPacket::name,
            ByteBufCodecs.FLOAT, UiPointerS2CPacket::x,
            ByteBufCodecs.FLOAT, UiPointerS2CPacket::y,
            UiPointerS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
