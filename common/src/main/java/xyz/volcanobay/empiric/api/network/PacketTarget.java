package xyz.volcanobay.empiric.api.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import xyz.volcanobay.empiric.client.ClientLookup;

/**
 * Where a packet goes.
 * One player, the players tracking something, a level, every player, or the server when sent from a
 * client. Built on vanilla only.
 */
@FunctionalInterface
public interface PacketTarget {

    /**
     * Sends nowhere.
     */
    PacketTarget NONE = payload -> {
    };

    void send(CustomPacketPayload payload);

    static PacketTarget player(ServerPlayer player) {
        return payload -> player.connection.send(clientbound(payload));
    }

    /**
     * Every player in the level.
     */
    static PacketTarget level(ServerLevel level) {
        return payload -> level.getServer().getPlayerList().broadcastAll(clientbound(payload), level.dimension());
    }

    /**
     * Every player on the server.
     */
    static PacketTarget all(MinecraftServer server) {
        return payload -> server.getPlayerList().broadcastAll(clientbound(payload));
    }

    /**
     * The players tracking the entity, not counting the entity itself.
     */
    static PacketTarget tracking(Entity entity) {
        ServerChunkCache chunks = chunks(entity);
        return payload -> chunks.broadcast(entity, clientbound(payload));
    }

    /**
     * The players tracking the entity, and the entity if it's a player.
     */
    static PacketTarget trackingAndSelf(Entity entity) {
        ServerChunkCache chunks = chunks(entity);
        return payload -> chunks.broadcastAndSend(entity, clientbound(payload));
    }

    /**
     * The players tracking the chunk.
     */
    static PacketTarget tracking(ServerLevel level, ChunkPos chunk) {
        return payload -> {
            Packet<?> packet = clientbound(payload);
            for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(chunk, false)) {
                player.connection.send(packet);
            }
        };
    }

    /**
     * The players tracking the chunk the position is in.
     */
    static PacketTarget tracking(ServerLevel level, BlockPos pos) {
        return tracking(level, new ChunkPos(pos));
    }

    /**
     * The players tracking the chunk the block entity is in.
     */
    static PacketTarget tracking(BlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) {
            throw new IllegalArgumentException("Block entity packets can only be sent from a server level");
        }
        return tracking(level, blockEntity.getBlockPos());
    }

    /**
     * The server, from the client.
     */
    static PacketTarget server() {
        return ClientLookup::sendToServer;
    }

    private static ServerChunkCache chunks(Entity entity) {
        if (!(entity.level().getChunkSource() instanceof ServerChunkCache chunks)) {
            throw new IllegalArgumentException("Entity packets can only be sent from a server level");
        }
        return chunks;
    }

    private static Packet<?> clientbound(CustomPacketPayload payload) {
        return new ClientboundCustomPayloadPacket(payload);
    }
}
