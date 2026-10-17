package xyz.volcanobay.empiric.networking.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.oct.OctPos;

public record OctBreakS2CPacket(OctPos pos, BlockState state) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OctBreakS2CPacket> TYPE = new CustomPacketPayload.Type<>(Empiric.id("oct_break"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OctBreakS2CPacket> CODEC = StreamCodec.composite(
            OctPos.STREAM_CODEC, OctBreakS2CPacket::pos,
            ByteBufCodecs.fromCodecWithRegistries(BlockState.CODEC), OctBreakS2CPacket::state,
            OctBreakS2CPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
