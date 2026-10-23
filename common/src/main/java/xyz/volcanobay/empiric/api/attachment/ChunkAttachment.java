package xyz.volcanobay.empiric.api.attachment;

import xyz.volcanobay.empiric.api.network.PacketTarget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import xyz.volcanobay.empiric.core.attachment.AttachmentType;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.DataContext;
import xyz.volcanobay.empiric.core.module.ModuleHolder;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.ext.ChunkExtension;
import xyz.volcanobay.empiric.core.spatial.SpatialObjectMap;
import xyz.volcanobay.empiric.system.SpatialModuleStore;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

import java.util.function.Consumer;

public final class ChunkAttachment extends PersistentAttachment {

    private final LevelChunk chunk;
    private final ModuleHolder modules = new ModuleHolder(this);
    private final Map<SpatialModuleStore<?>, SpatialObjectMap<?>> spatial = new IdentityHashMap<>();
    private CompoundTag pendingSpatial;

    public ChunkAttachment(LevelChunk chunk) {
        this.chunk = chunk;
    }

    @SuppressWarnings("unchecked")
    public <A extends SpatialAttachment> SpatialObjectMap<A> spatial(SpatialModuleStore<A> store) {
        return (SpatialObjectMap<A>) spatial.computeIfAbsent(store, key -> new SpatialObjectMap<>(16));
    }

    public CompoundTag pendingSpatial() {
        return pendingSpatial;
    }

    public void pendingSpatial(CompoundTag pendingSpatial) {
        this.pendingSpatial = pendingSpatial;
    }

    public static ChunkAttachment of(LevelChunk chunk) {
        return ((ChunkExtension) chunk).empiric$attachment();
    }

    public LevelChunk chunk() {
        return chunk;
    }

    public ChunkPos pos() {
        return chunk.getPos();
    }

    @Override
    public Level level() {
        return chunk.getLevel();
    }

    @Override
    public AttachmentType<?, ?> attachmentType() {
        return EmpiricAttachments.CHUNK;
    }

    @Override
    public ModuleHolder modules() {
        return modules;
    }

    @Override
    public PacketTarget syncTarget() {
        return PacketTarget.tracking((ServerLevel) chunk.getLevel(), chunk.getPos());
    }

    @Override
    public Collection<ServerPlayer> viewers() {
        return WorldAttachment.trackingChunk(chunk.getLevel(), chunk.getPos());
    }

    @Override
    public Vec3 position() {
        return new Vec3(pos().getMiddleBlockX(), Double.NaN, pos().getMiddleBlockZ());
    }

    public static final class Type extends AttachmentType<ChunkAttachment, LevelChunk> {

        Type() {
            super("empiric:chunk");
        }

        @Override
        public ChunkAttachment get(LevelChunk chunk) {
            return of(chunk);
        }

        @Override
        public void writeKey(DataBuffer buffer, ChunkAttachment attachment, DataContext context) {
            ChunkPos pos = attachment.chunk.getPos();
            buffer.putSignedVarInt(pos.x);
            buffer.putSignedVarInt(pos.z);
        }

        @Override
        public ChunkAttachment readKey(DataBuffer buffer, Side side, DataContext context) {
            int x = buffer.takeSignedVarInt();
            int z = buffer.takeSignedVarInt();
            Level level = WorldModuleSystem.keyLevel(side);
            ChunkAccess chunk = level == null ? null : level.getChunk(x, z, ChunkStatus.FULL, false);
            return chunk instanceof LevelChunk levelChunk ? of(levelChunk) : null;
        }

        @Override
        public void forEachLoaded(Side side, Consumer<ChunkAttachment> action) {
            WorldModuleSystem.forEachChunk(side, action);
        }
    }
}
