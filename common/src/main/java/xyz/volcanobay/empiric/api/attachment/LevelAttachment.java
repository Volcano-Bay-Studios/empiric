package xyz.volcanobay.empiric.api.attachment;

import xyz.volcanobay.empiric.api.network.PacketTarget;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.client.ClientLookup;
import xyz.volcanobay.empiric.core.attachment.AttachmentType;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.DataContext;
import xyz.volcanobay.empiric.core.module.ModuleHolder;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.core.spatial.SpatialObjectMap;
import xyz.volcanobay.empiric.ext.LevelExtension;
import xyz.volcanobay.empiric.api.network.NetworkModule;
import xyz.volcanobay.empiric.api.network.SpatialNetworkMap;
import xyz.volcanobay.empiric.api.network.SpatialNetworkType;
import xyz.volcanobay.empiric.system.SpatialModuleStore;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import java.util.function.Consumer;

public final class LevelAttachment implements WorldAttachment {

    private final Level level;
    private final ModuleHolder modules = new ModuleHolder(this);
    private final Map<SpatialModuleStore<?>, SpatialObjectMap<?>> spatial = new IdentityHashMap<>();
    private final Map<SpatialNetworkType<?>, SpatialNetworkMap<?>> networks = new IdentityHashMap<>();

    public LevelAttachment(Level level) {
        this.level = level;
    }

    @SuppressWarnings("unchecked")
    public <N extends NetworkModule> SpatialNetworkMap<N> network(SpatialNetworkType<N> type) {
        return (SpatialNetworkMap<N>) networks.computeIfAbsent(type, key -> new SpatialNetworkMap<>(level, type));
    }

    public void forEachNetwork(Consumer<SpatialNetworkMap<?>> action) {
        List.copyOf(networks.values()).forEach(action);
    }

    @SuppressWarnings("unchecked")
    public <A extends SpatialAttachment> SpatialObjectMap<A> spatial(SpatialModuleStore<A> store) {
        return (SpatialObjectMap<A>) spatial.computeIfAbsent(store, key -> new SpatialObjectMap<>(64));
    }

    public static LevelAttachment of(Level level) {
        return ((LevelExtension) level).empiric$attachment();
    }

    @Override
    public Level level() {
        return level;
    }

    @Override
    public AttachmentType<?, ?> attachmentType() {
        return EmpiricAttachments.LEVEL;
    }

    @Override
    public ModuleHolder modules() {
        return modules;
    }

    @Override
    public PacketTarget syncTarget() {
        return PacketTarget.level((ServerLevel) level);
    }

    @Override
    public Collection<ServerPlayer> viewers() {
        return ((ServerLevel) level).players();
    }

    public static final class Type extends AttachmentType<LevelAttachment, Level> {

        Type() {
            super("empiric:level");
        }

        @Override
        public LevelAttachment get(Level level) {
            return of(level);
        }

        @Override
        public void writeKey(DataBuffer buffer, LevelAttachment attachment, DataContext context) {
            buffer.putString(attachment.level.dimension().location().toString());
        }

        @Override
        public LevelAttachment readKey(DataBuffer buffer, Side side, DataContext context) {
            ResourceLocation dimension = ResourceLocation.parse(buffer.takeString());
            Level level = null;
            if (side == Side.CLIENT) {
                level = ClientLookup.level();
            } else {
                MinecraftServer server = WorldModuleSystem.server();
                if (server != null) {
                    level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
                }
            }
            return level != null && level.dimension().location().equals(dimension) ? of(level) : null;
        }

        @Override
        public void forEachLoaded(Side side, Consumer<LevelAttachment> action) {
            if (side == Side.CLIENT) {
                Level level = ClientLookup.level();
                if (level != null) {
                    action.accept(of(level));
                }
                return;
            }
            MinecraftServer server = WorldModuleSystem.server();
            if (server != null) {
                for (ServerLevel level : server.getAllLevels()) {
                    action.accept(of(level));
                }
            }
        }
    }
}
