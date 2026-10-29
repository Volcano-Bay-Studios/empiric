package xyz.volcanobay.empiric.system;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import xyz.volcanobay.empiric.api.attachment.BlockEntityAttachment;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.attachment.EntityAttachment;
import xyz.volcanobay.empiric.api.attachment.LevelAttachment;
import xyz.volcanobay.empiric.api.attachment.PersistentAttachment;
import xyz.volcanobay.empiric.api.attachment.ServerAttachment;
import xyz.volcanobay.empiric.api.attachment.WorldAttachment;
import xyz.volcanobay.empiric.api.behavior.BlockChangeBehavior;
import xyz.volcanobay.empiric.api.behavior.EmpiricBehaviors;
import xyz.volcanobay.empiric.client.ClientLookup;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.attachment.Attachment;
import xyz.volcanobay.empiric.core.attachment.ModuleAttachment;
import xyz.volcanobay.empiric.core.data.Encoding;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.data.EmpiricBuffers;
import xyz.volcanobay.empiric.ext.ChunkExtension;
import xyz.volcanobay.empiric.persistent.ModuleSavedData;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class WorldModuleSystem {

    public static final String TAG = "empiric:modules";

    private static final ThreadLocal<Level> CONTEXT = new ThreadLocal<>();
    private static final List<Set<BlockEntityAttachment>> BLOCK_ENTITIES = List.of(
            Collections.newSetFromMap(new IdentityHashMap<>()),
            Collections.newSetFromMap(new IdentityHashMap<>()));
    private static final List<Set<ChunkAttachment>> CHUNKS = List.of(
            Collections.newSetFromMap(new IdentityHashMap<>()),
            Collections.newSetFromMap(new IdentityHashMap<>()));
    private static volatile MinecraftServer server;

    private WorldModuleSystem() {
    }

    public static MinecraftServer server() {
        return server;
    }

    public static void serverStarting(MinecraftServer server) {
        WorldModuleSystem.server = server;
        EmpiricBuffers.registries(server.registryAccess());
    }

    public static Level keyLevel(Side side) {
        if (side == Side.CLIENT) {
            return ClientLookup.level();
        }
        return side == Side.SERVER ? CONTEXT.get() : null;
    }

    public static <T> T withLevel(Level level, Supplier<T> action) {
        Level previous = CONTEXT.get();
        CONTEXT.set(level);
        try {
            return action.get();
        } finally {
            CONTEXT.set(previous);
        }
    }

    public static void serverStopped() {
        EmpiricCore.modules().unloadSide(Side.SERVER);
        ServerAttachment.reset(Side.SERVER);
        DebugSubscriptionSystem.clear();
        UiSystem.clear();
        BLOCK_ENTITIES.get(Side.SERVER.ordinal()).clear();
        CHUNKS.get(Side.SERVER.ordinal()).clear();
        server = null;
    }

    public static void clientLevelChanged(Level level) {
        EmpiricCore.modules().unloadSide(Side.CLIENT);
        ServerAttachment.reset(Side.CLIENT);
        BLOCK_ENTITIES.get(Side.CLIENT.ordinal()).clear();
        CHUNKS.get(Side.CLIENT.ordinal()).clear();
        if (level != null) {
            EmpiricBuffers.registries(level.registryAccess());
            EmpiricCore.modules().applyRules(LevelAttachment.of(level));
            EmpiricCore.modules().applyRules(ServerAttachment.of(Side.CLIENT));
        }
    }

    public static void loadLevel(ServerLevel level) {
        ModuleSystem modules = EmpiricCore.modules();
        LevelAttachment attachment = LevelAttachment.of(level);
        ModuleSavedData data = ModuleSavedData.get(level);
        if (level.dimension() == Level.OVERWORLD) {
            byte[] statics = data.takeStatics();
            if (statics != null) {
                modules.loadStatics(EmpiricBuffers.buffer(statics), modules.specs().context(Encoding.KEYED));
            }
            ServerAttachment global = ServerAttachment.of(Side.SERVER);
            byte[] saved = data.takeServer();
            if (saved != null) {
                modules.load(global, EmpiricBuffers.buffer(saved), modules.specs().context(Encoding.KEYED));
            }
            modules.applyRules(global);
        }
        byte[] bytes = data.takeLoaded();
        if (bytes != null) {
            modules.load(attachment, EmpiricBuffers.buffer(bytes), modules.specs().context(Encoding.KEYED));
        }
        modules.applyRules(attachment);
        data.loadNetworks();
    }

    public static void unloadLevel(Level level) {
        SpatialNetworkSystem.unload(level);
        EmpiricCore.modules().unload(LevelAttachment.of(level));
    }

    public static byte[] encode(Attachment attachment) {
        return encodeAll(attachment, ModuleType::persists);
    }

    public static byte[] encodeAll(Attachment attachment, Predicate<ModuleType<?, ?>> filter) {
        ModuleSystem modules = EmpiricCore.modules();
        RegistryFriendlyByteBuf buffer = EmpiricBuffers.create();
        modules.save(attachment, EmpiricBuffers.of(buffer), modules.specs().context(Encoding.KEYED), filter);
        return EmpiricBuffers.bytes(buffer);
    }

    public static byte[] encodeStatics() {
        ModuleSystem modules = EmpiricCore.modules();
        RegistryFriendlyByteBuf buffer = EmpiricBuffers.create();
        modules.saveStatics(EmpiricBuffers.of(buffer), modules.specs().context(Encoding.KEYED));
        return EmpiricBuffers.bytes(buffer);
    }

    public static void save(PersistentAttachment attachment, CompoundTag tag) {
        if (!attachment.isActive()) {
            byte[] pending = attachment.pending();
            if (pending != null) {
                tag.putByteArray(TAG, pending);
            }
            return;
        }
        if (!attachment.modules().isEmpty()) {
            Predicate<ModuleType<?, ?>> filter = attachment instanceof EntityAttachment entity ? EntityDropSystem.saveFilter(entity) : ModuleType::persists;
            tag.putByteArray(TAG, encodeAll(attachment, filter));
        }
    }

    public static void copy(PersistentAttachment source, PersistentAttachment target, Predicate<ModuleType<?, ?>> filter) {
        ModuleSystem modules = EmpiricCore.modules();
        modules.load(target, EmpiricBuffers.buffer(encodeAll(source, filter)), modules.specs().context(Encoding.KEYED));
    }

    public static void read(PersistentAttachment attachment, CompoundTag tag) {
        if (!tag.contains(TAG)) {
            return;
        }
        attachment.pending(tag.getByteArray(TAG));
        if (attachment.isActive()) {
            activatePending(attachment);
        }
    }

    private static void activatePending(PersistentAttachment attachment) {
        byte[] pending = attachment.takePending();
        if (pending != null) {
            ModuleSystem modules = EmpiricCore.modules();
            modules.load(attachment, EmpiricBuffers.buffer(pending), modules.specs().context(Encoding.KEYED));
        }
    }

    static void activate(PersistentAttachment attachment) {
        if (attachment.isActive() || attachment.side() == Side.NONE) {
            return;
        }
        attachment.active(true);
        activatePending(attachment);
        EmpiricCore.modules().applyRules(attachment);
    }

    public static void entityLoaded(Entity entity) {
        activate(EntityAttachment.of(entity));
    }

    public static void entityUnloaded(Entity entity) {
        EntityAttachment attachment = EntityAttachment.of(entity);
        if (!attachment.isActive()) {
            return;
        }
        if (EntityDropSystem.held(attachment)) {
            attachment.active(false);
            return;
        }
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason != null && reason.shouldDestroy()) {
            EmpiricCore.modules().detachAll(attachment);
        } else if (reason == Entity.RemovalReason.CHANGED_DIMENSION && entity instanceof ServerPlayer player) {
            EntityDropSystem.changingLevel(player);
            EmpiricCore.modules().unload(attachment);
        } else {
            EmpiricCore.modules().unload(attachment);
        }
        attachment.active(false);
    }

    public static void blockEntityLoaded(BlockEntity blockEntity) {
        BlockEntityAttachment attachment = BlockEntityAttachment.of(blockEntity);
        if (attachment.isActive() || blockEntity.getLevel() == null) {
            return;
        }
        BLOCK_ENTITIES.get(attachment.side().ordinal()).add(attachment);
        activate(attachment);
    }

    public static void blockEntityUnloaded(BlockEntity blockEntity) {
        BlockEntityAttachment attachment = BlockEntityAttachment.of(blockEntity);
        if (!attachment.isActive()) {
            return;
        }
        BLOCK_ENTITIES.get(attachment.side().ordinal()).remove(attachment);
        if (!attachment.modules().isEmpty()) {
            attachment.pending(encodeAll(attachment, ModuleType::persists));
        }
        EmpiricCore.modules().unload(attachment);
        attachment.active(false);
    }

    public static void blockEntityDestroyed(BlockEntity blockEntity) {
        BlockEntityAttachment attachment = BlockEntityAttachment.of(blockEntity);
        if (attachment.isActive()) {
            EmpiricCore.modules().detachAll(attachment);
        }
    }

    public static void forEachBlockEntity(Side side, Consumer<BlockEntityAttachment> action) {
        if (side == Side.NONE) {
            return;
        }
        for (BlockEntityAttachment attachment : List.copyOf(BLOCK_ENTITIES.get(side.ordinal()))) {
            action.accept(attachment);
        }
    }

    public static void saveChunk(ChunkAccess chunk, CompoundTag tag) {
        LevelChunk full = chunk instanceof ImposterProtoChunk imposter ? imposter.getWrapped() : chunk instanceof LevelChunk levelChunk ? levelChunk : null;
        if (full != null) {
            ChunkAttachment attachment = ChunkAttachment.of(full);
            save(attachment, tag);
            SpatialModuleStore.saveAll(attachment, tag);
            return;
        }
        ChunkExtension extension = (ChunkExtension) chunk;
        if (extension.empiric$pending() != null) {
            tag.putByteArray(TAG, extension.empiric$pending());
        }
        CompoundTag pendingSpatial = extension.empiric$pendingSpatial();
        if (pendingSpatial != null) {
            for (String key : pendingSpatial.getAllKeys()) {
                tag.put(key, pendingSpatial.get(key));
            }
        }
    }

    public static void readChunk(ProtoChunk chunk, CompoundTag tag) {
        byte[] bytes = tag.contains(TAG) ? tag.getByteArray(TAG) : null;
        CompoundTag spatial = SpatialModuleStore.readAll(tag);
        if (bytes == null && spatial == null) {
            return;
        }
        if (chunk instanceof ImposterProtoChunk imposter) {
            ChunkAttachment attachment = ChunkAttachment.of(imposter.getWrapped());
            attachment.pending(bytes);
            attachment.pendingSpatial(spatial);
        } else {
            ((ChunkExtension) chunk).empiric$pending(bytes);
            ((ChunkExtension) chunk).empiric$pendingSpatial(spatial);
        }
    }

    public static void promoteChunk(ProtoChunk proto, LevelChunk chunk) {
        ChunkExtension extension = (ChunkExtension) proto;
        ChunkAttachment attachment = ChunkAttachment.of(chunk);
        if (extension.empiric$pending() != null) {
            attachment.pending(extension.empiric$pending());
            extension.empiric$pending(null);
        }
        if (extension.empiric$pendingSpatial() != null) {
            attachment.pendingSpatial(extension.empiric$pendingSpatial());
            extension.empiric$pendingSpatial(null);
        }
    }

    public static void chunkLoaded(LevelChunk chunk) {
        ChunkAttachment attachment = ChunkAttachment.of(chunk);
        if (attachment.isActive()) {
            return;
        }
        CHUNKS.get(attachment.side().ordinal()).add(attachment);
        activate(attachment);
        SpatialModuleStore.loadAll(attachment);
        SpatialNetworkSystem.chunkLoaded(chunk.getLevel(), chunk.getPos());
    }

    public static void chunkUnloaded(LevelChunk chunk) {
        ChunkAttachment attachment = ChunkAttachment.of(chunk);
        if (!attachment.isActive()) {
            return;
        }
        CHUNKS.get(attachment.side().ordinal()).remove(attachment);
        CompoundTag spatial = new CompoundTag();
        SpatialModuleStore.saveAll(attachment, spatial);
        if (!spatial.isEmpty()) {
            attachment.pendingSpatial(spatial);
        }
        if (!attachment.modules().isEmpty()) {
            attachment.pending(encodeAll(attachment, ModuleType::persists));
        }
        SpatialModuleStore.unloadAll(attachment);
        EmpiricCore.modules().unload(attachment);
        attachment.active(false);
    }

    public static void forEachChunk(Side side, Consumer<ChunkAttachment> action) {
        if (side == Side.NONE) {
            return;
        }
        for (ChunkAttachment attachment : List.copyOf(CHUNKS.get(side.ordinal()))) {
            action.accept(attachment);
        }
    }

    public static void blockChanged(Level level, BlockPos pos, BlockState oldState, BlockState newState) {
        BlockModuleSystem.blockChanged(level, pos, oldState, newState);
        OctModuleSystem.blockChanged(level, pos, oldState, newState);
        OctMultiblockSystem.blockChanged(level, pos, oldState, newState);
        if (!level.isClientSide) {
            TemperatureSystem.blockChanged(level, pos);
            SpatialNetworkSystem.blockChanged(level, pos);
        }
        ModuleSystem modules = EmpiricCore.modules();
        Side side = level.isClientSide ? Side.CLIENT : Side.SERVER;
        if (modules.count(side, EmpiricBehaviors.BLOCK_CHANGE) == 0) {
            return;
        }
        modules.forEach(side, EmpiricBehaviors.BLOCK_CHANGE, (BlockChangeBehavior behavior) -> {
            if (levelOf((Module<?>) behavior) == level) {
                behavior.onBlockChanged(pos, oldState, newState);
            }
        });
    }

    public static Level levelOf(Module<?> module) {
        Attachment attachment = module.attachment();
        while (attachment instanceof ModuleAttachment moduleAttachment) {
            attachment = moduleAttachment.parent().attachment();
        }
        return attachment instanceof WorldAttachment world ? world.level() : null;
    }
}
