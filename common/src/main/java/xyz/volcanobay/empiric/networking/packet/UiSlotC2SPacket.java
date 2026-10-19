package xyz.volcanobay.empiric.networking.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.volcanobay.empiric.Empiric;

/**
 * A click on a slot of a screen shown without a menu, such as on a world surface. The slot is its index among the
 * screen's slots in tree order.
 */
public record UiSlotC2SPacket(byte[] data, int slot, int button) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UiSlotC2SPacket> TYPE = new CustomPacketPayload.Type<>(Empiric.id("ui_slot"));
    public static final StreamCodec<ByteBuf, UiSlotC2SPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE_ARRAY, UiSlotC2SPacket::data,
            ByteBufCodecs.VAR_INT, UiSlotC2SPacket::slot,
            ByteBufCodecs.VAR_INT, UiSlotC2SPacket::button,
            UiSlotC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
