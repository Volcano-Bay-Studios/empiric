package xyz.volcanobay.empiric.networking.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.volcanobay.empiric.Empiric;

/**
 * Opens the layout editor on the client, on a layout or, if empty, on the list of layouts.
 */
public record UiEditorS2CPacket(String layout) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UiEditorS2CPacket> TYPE = new CustomPacketPayload.Type<>(Empiric.id("ui_editor"));
    public static final StreamCodec<ByteBuf, UiEditorS2CPacket> CODEC = ByteBufCodecs.STRING_UTF8.map(UiEditorS2CPacket::new, UiEditorS2CPacket::layout);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
