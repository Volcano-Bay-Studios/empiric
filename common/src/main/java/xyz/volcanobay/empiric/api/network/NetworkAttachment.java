package xyz.volcanobay.empiric.api.network;

import foundry.veil.api.network.VeilPacketManager;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.api.attachment.WorldAttachment;
import xyz.volcanobay.empiric.core.attachment.AttachmentType;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.DataContext;
import xyz.volcanobay.empiric.core.module.ModuleHolder;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.core.spatial.ManagedSpatialNetwork;
import xyz.volcanobay.empiric.system.ModuleSyncSystem;
import xyz.volcanobay.empiric.system.SpatialNetworkSystem;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class NetworkAttachment implements WorldAttachment {

    private final SpatialNetworkMap<?> map;
    private final int id;
    private final ModuleHolder modules = new ModuleHolder(this);
    private final Set<UUID> viewers = new HashSet<>();
    private LongSet chunks;

    public NetworkAttachment(SpatialNetworkMap<?> map, int id) {
        this.map = map;
        this.id = id;
    }

    public SpatialNetworkMap<?> map() {
        return map;
    }

    public int id() {
        return id;
    }

    @Override
    public Level level() {
        return map.level();
    }

    @Override
    public AttachmentType<?, ?> attachmentType() {
        return EmpiricAttachments.NETWORK;
    }

    @Override
    public ModuleHolder modules() {
        return modules;
    }

    public LongSet chunks() {
        if (chunks == null) {
            chunks = new LongOpenHashSet();
            for (ManagedSpatialNetwork.SpatialPos member : map.getNetworkMembers(id)) {
                chunks.add(ChunkPos.asLong((int) Math.floorDiv(member.x(), 32L), (int) Math.floorDiv(member.z(), 32L)));
            }
        }
        return chunks;
    }

    public void invalidateChunks() {
        chunks = null;
    }

    public boolean addViewer(ServerPlayer player) {
        return viewers.add(player.getUUID());
    }

    public void removeViewer(ServerPlayer player) {
        viewers.remove(player.getUUID());
    }

    public boolean isViewer(ServerPlayer player) {
        return viewers.contains(player.getUUID());
    }

    public boolean tracks(ServerPlayer player, long ignored) {
        ChunkTrackingView view = player.getChunkTrackingView();
        for (long chunk : chunks()) {
            if (chunk != ignored && view.contains(ChunkPos.getX(chunk), ChunkPos.getZ(chunk))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public VeilPacketManager.PacketSink syncTarget() {
        ServerLevel level = (ServerLevel) map.level();
        if (id >= 0) {
            for (ServerPlayer player : level.players()) {
                if (!viewers.contains(player.getUUID()) && tracks(player, Long.MIN_VALUE)) {
                    viewers.add(player.getUUID());
                    ModuleSyncSystem.syncTo(player, this);
                }
            }
        }
        return packet -> {
            for (UUID viewer : viewers) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(viewer);
                if (player != null && player.level() == level) {
                    player.connection.send(packet);
                }
            }
        };
    }

    public record Key(SpatialNetworkMap<?> map, int id) {
    }

    public static final class Type extends AttachmentType<NetworkAttachment, Key> {

        public Type() {
            super("empiric:network");
        }

        @Override
        public NetworkAttachment get(Key key) {
            return key.map().attachment(key.id());
        }

        @Override
        public void writeKey(DataBuffer buffer, NetworkAttachment attachment, DataContext context) {
            buffer.putVarInt(attachment.map.type().index());
            buffer.putSignedVarInt(attachment.id);
        }

        @Override
        public NetworkAttachment readKey(DataBuffer buffer, Side side, DataContext context) {
            SpatialNetworkType<?> type = SpatialNetworkSystem.type(buffer.takeVarInt());
            int id = buffer.takeSignedVarInt();
            if (type == null) {
                return null;
            }
            Level level = WorldModuleSystem.keyLevel(side);
            if (level == null) {
                return null;
            }
            return side == Side.SERVER ? type.get(level).existing(id) : type.get(level).attachment(id);
        }

        @Override
        public void forEachLoaded(Side side, Consumer<NetworkAttachment> action) {
            SpatialNetworkSystem.forEach(side, action);
        }
    }
}
