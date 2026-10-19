package xyz.volcanobay.empiric.networking.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.volcanobay.empiric.Empiric;

/**
 * Shows a screen on a surface hanging in the world, centred on a point and turned to face the given yaw. This is for debug and demos.
 */
public record UiSurfaceS2CPacket(byte[] data, double x, double y, double z, float yaw, float width) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UiSurfaceS2CPacket> TYPE = new CustomPacketPayload.Type<>(Empiric.id("ui_surface"));
    public static final StreamCodec<ByteBuf, UiSurfaceS2CPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE_ARRAY, UiSurfaceS2CPacket::data,
            ByteBufCodecs.DOUBLE, UiSurfaceS2CPacket::x,
            ByteBufCodecs.DOUBLE, UiSurfaceS2CPacket::y,
            ByteBufCodecs.DOUBLE, UiSurfaceS2CPacket::z,
            ByteBufCodecs.FLOAT, UiSurfaceS2CPacket::yaw,
            ByteBufCodecs.FLOAT, UiSurfaceS2CPacket::width,
            UiSurfaceS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
