package xyz.volcanobay.empiric.networking.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.volcanobay.empiric.Empiric;

/**
 * Asks the server to open a screen, which it answers with {@link UiOpenS2CPacket} if the module allows it.
 */
public record UiOpenC2SPacket(byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UiOpenC2SPacket> TYPE = new CustomPacketPayload.Type<>(Empiric.id("ui_request"));
    public static final StreamCodec<ByteBuf, UiOpenC2SPacket> CODEC = ByteBufCodecs.BYTE_ARRAY.map(UiOpenC2SPacket::new, UiOpenC2SPacket::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
