package xyz.volcanobay.empiric.networking.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.volcanobay.empiric.Empiric;

/**
 * Shows or hides the client's temperature overlay. A radius of 0 hides it.
 */
public record TemperatureOverlayS2CPacket(int radius) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TemperatureOverlayS2CPacket> TYPE = new CustomPacketPayload.Type<>(Empiric.id("temperature_overlay"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TemperatureOverlayS2CPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TemperatureOverlayS2CPacket::radius,
            TemperatureOverlayS2CPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
