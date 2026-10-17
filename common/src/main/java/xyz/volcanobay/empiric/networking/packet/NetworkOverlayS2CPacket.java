package xyz.volcanobay.empiric.networking.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.core.spatial.ManagedSpatialNetwork.SpatialPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The networks of one type a player is viewing. A hidden packet removes the type's overlay.
 */
public record NetworkOverlayS2CPacket(String networkType, boolean visible, List<Network> networks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NetworkOverlayS2CPacket> TYPE = new CustomPacketPayload.Type<>(Empiric.id("network_overlay"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkOverlayS2CPacket> CODEC = StreamCodec.of(NetworkOverlayS2CPacket::write, NetworkOverlayS2CPacket::read);

    public static NetworkOverlayS2CPacket hidden(String networkType) {
        return new NetworkOverlayS2CPacket(networkType, false, List.of());
    }

    private static void write(RegistryFriendlyByteBuf buffer, NetworkOverlayS2CPacket packet) {
        buffer.writeUtf(packet.networkType);
        buffer.writeBoolean(packet.visible);
        buffer.writeVarInt(packet.networks.size());
        for (Network network : packet.networks) {
            buffer.writeVarInt(network.id());
            buffer.writeVarInt(network.members().size());
            for (SpatialPos member : network.members()) {
                buffer.writeVarLong(member.x());
                buffer.writeVarLong(member.y());
                buffer.writeVarLong(member.z());
            }
        }
    }

    private static NetworkOverlayS2CPacket read(RegistryFriendlyByteBuf buffer) {
        String networkType = buffer.readUtf();
        boolean visible = buffer.readBoolean();
        int count = buffer.readVarInt();
        List<Network> networks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = buffer.readVarInt();
            int size = buffer.readVarInt();
            List<SpatialPos> members = new ArrayList<>(size);
            for (int j = 0; j < size; j++) {
                members.add(new SpatialPos(buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong()));
            }
            networks.add(new Network(id, members));
        }
        return new NetworkOverlayS2CPacket(networkType, visible, networks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Network(int id, List<SpatialPos> members) {
    }
}
