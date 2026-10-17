package xyz.volcanobay.empiric.system;

import foundry.veil.api.network.VeilPacketManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import xyz.volcanobay.empiric.core.spatial.ManagedSpatialNetwork;
import xyz.volcanobay.empiric.networking.packet.ModuleRemoveS2CPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.attachment.LevelAttachment;
import xyz.volcanobay.empiric.api.network.NetworkAttachment;
import xyz.volcanobay.empiric.api.network.NetworkModule;
import xyz.volcanobay.empiric.api.network.SpatialNetworkMap;
import xyz.volcanobay.empiric.api.network.SpatialNetworkType;
import xyz.volcanobay.empiric.client.ClientLookup;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.Side;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public final class SpatialNetworkSystem {

    private static final List<SpatialNetworkType<?>> TYPES = new ArrayList<>();
    private static final List<Dropped> DROPPED = new ArrayList<>();

    private SpatialNetworkSystem() {
    }

    public static <N extends NetworkModule> SpatialNetworkType<N> register(ModuleSystem modules, SpatialNetworkType<N> type) {
        modules.register(type.moduleType());
        type.assignIndex(TYPES.size());
        TYPES.add(type);
        return type;
    }

    public static List<SpatialNetworkType<?>> types() {
        return Collections.unmodifiableList(TYPES);
    }

    public static SpatialNetworkType<?> type(int index) {
        return index >= 0 && index < TYPES.size() ? TYPES.get(index) : null;
    }

    public static <N extends NetworkModule> SpatialNetworkMap<N> map(Level level, SpatialNetworkType<N> type) {
        return LevelAttachment.of(level).network(type);
    }

    public static SpatialNetworkType<?> type(String id) {
        for (SpatialNetworkType<?> type : TYPES) {
            if (type.id().toString().equals(id)) {
                return type;
            }
        }
        return null;
    }

    public static void chunkSent(ServerPlayer player, ChunkPos chunk, Consumer<NetworkAttachment> sync) {
        long key = chunk.toLong();
        LevelAttachment.of(player.serverLevel()).forEachNetwork(map -> {
            for (NetworkAttachment attachment : map.attachments()) {
                if (attachment.id() >= 0 && attachment.chunks().contains(key) && attachment.addViewer(player)) {
                    sync.accept(attachment);
                }
            }
        });
    }

    public static void chunkDropped(ServerPlayer player, ChunkPos chunk) {
        DROPPED.add(new Dropped(player, chunk.toLong()));
    }

    public static void processDropped() {
        if (DROPPED.isEmpty()) {
            return;
        }
        List<Dropped> dropped = new ArrayList<>(DROPPED);
        DROPPED.clear();
        for (Dropped entry : dropped) {
            ServerPlayer player = entry.player();
            if (player.isRemoved()) {
                continue;
            }
            LevelAttachment.of(player.serverLevel()).forEachNetwork(map -> {
                for (NetworkAttachment attachment : map.attachments()) {
                    if (!attachment.isViewer(player) || !attachment.chunks().contains(entry.chunk()) || attachment.tracks(player, Long.MIN_VALUE)) {
                        continue;
                    }
                    attachment.removeViewer(player);
                    VeilPacketManager.PacketSink sink = VeilPacketManager.player(player);
                    attachment.modules().forEach(module -> {
                        try {
                            sink.sendPacket(ModuleRemoveS2CPacket.of(module));
                        } catch (UnsupportedOperationException e) {
                            return;
                        }
                    });
                }
            });
        }
    }

    public static void forgetViewer(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            LevelAttachment.of(level).forEachNetwork(map -> map.attachments().forEach(attachment -> attachment.removeViewer(player)));
        }
    }

    public static void chunkLoaded(Level level, ChunkPos chunk) {
        LevelAttachment.of(level).forEachNetwork(map -> map.chunkLoaded(chunk));
    }

    public static ListTag save(Level level, ListTag unknown) {
        ListTag list = unknown.copy();
        for (SpatialNetworkType<?> type : TYPES) {
            for (SpatialNetworkMap.Saved saved : type.get(level).save()) {
                CompoundTag tag = new CompoundTag();
                tag.putString("type", type.id().toString());
                tag.putLong("x", saved.anchor().x());
                tag.putLong("y", saved.anchor().y());
                tag.putLong("z", saved.anchor().z());
                tag.putByteArray("data", saved.data());
                list.add(tag);
            }
        }
        return list;
    }

    public static ListTag load(Level level, ListTag list) {
        ListTag unknown = new ListTag();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            SpatialNetworkType<?> type = type(tag.getString("type"));
            if (type == null) {
                unknown.add(tag);
                continue;
            }
            ManagedSpatialNetwork.SpatialPos anchor = new ManagedSpatialNetwork.SpatialPos(tag.getLong("x"), tag.getLong("y"), tag.getLong("z"));
            type.get(level).load(new SpatialNetworkMap.Saved(anchor, tag.getByteArray("data")));
        }
        return unknown;
    }

    public static void unload(Level level) {
        LevelAttachment.of(level).forEachNetwork(SpatialNetworkMap::unload);
    }

    public static void forEach(Side side, Consumer<NetworkAttachment> action) {
        if (side == Side.CLIENT) {
            Level level = ClientLookup.level();
            if (level != null) {
                forEach(level, action);
            }
            return;
        }
        MinecraftServer server = WorldModuleSystem.server();
        if (side == Side.SERVER && server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                forEach(level, action);
            }
        }
    }

    private static void forEach(Level level, Consumer<NetworkAttachment> action) {
        LevelAttachment.of(level).forEachNetwork(map -> map.attachments().forEach(action));
    }

    private record Dropped(ServerPlayer player, long chunk) {
    }
}
