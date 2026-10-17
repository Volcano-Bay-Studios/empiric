package xyz.volcanobay.empiric.system;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.attachment.LevelAttachment;
import xyz.volcanobay.empiric.api.attachment.SpatialAttachment;
import xyz.volcanobay.empiric.client.ClientLookup;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.data.Encoding;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.core.spatial.SpatialObjectMap;
import xyz.volcanobay.empiric.data.EmpiricBuffers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public final class SpatialModuleStore<A extends SpatialAttachment> {

    private static final List<SpatialModuleStore<?>> STORES = new ArrayList<>();

    private final String tag;
    private final BiFunction<Level, CompoundTag, A> reader;
    private final BiConsumer<A, CompoundTag> writer;

    public SpatialModuleStore(String tag, BiFunction<Level, CompoundTag, A> reader, BiConsumer<A, CompoundTag> writer) {
        this.tag = tag;
        this.reader = reader;
        this.writer = writer;
        if (EmpiricCore.isBootstrapped()) {
            throw new IllegalStateException("Spatial module store " + tag + " was created after Empiric bootstrapped");
        }
        STORES.add(this);
    }

    public static List<SpatialModuleStore<?>> stores() {
        return Collections.unmodifiableList(STORES);
    }

    public String tag() {
        return tag;
    }

    public SpatialObjectMap<A> map(Level level) {
        return LevelAttachment.of(level).spatial(this);
    }

    public A find(Level level, long x, long y, long z) {
        SpatialObjectMap<A> map = map(level);
        return map.isEmpty() ? null : map.get(x, y, z);
    }

    public boolean isEmpty(Level level) {
        return map(level).isEmpty();
    }

    public A track(A attachment) {
        BlockPos pos = attachment.blockPos();
        LevelChunk chunk = attachment.level().getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        if (chunk == null) {
            throw new IllegalStateException("Modules can only attach in loaded chunks, " + pos + " isn't loaded");
        }
        track(ChunkAttachment.of(chunk), attachment);
        return attachment;
    }

    private void track(ChunkAttachment chunk, A attachment) {
        chunk.spatial(this).put(attachment.x(), attachment.y(), attachment.z(), attachment);
        map(attachment.level()).put(attachment.x(), attachment.y(), attachment.z(), attachment);
    }

    public void forget(A attachment) {
        map(attachment.level()).remove(attachment.x(), attachment.y(), attachment.z());
        BlockPos pos = attachment.blockPos();
        LevelChunk chunk = attachment.level().getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        if (chunk != null) {
            ChunkAttachment.of(chunk).spatial(this).remove(attachment.x(), attachment.y(), attachment.z());
        }
    }

    public void prune(A attachment) {
        if (attachment.modules().isEmpty()) {
            forget(attachment);
        }
    }

    public void forEachIn(ChunkAttachment chunk, Consumer<A> action) {
        chunk.spatial(this).forEach((x, y, z, attachment) -> action.accept(attachment));
    }

    public void save(ChunkAttachment chunk, CompoundTag chunkTag) {
        if (!chunk.isActive()) {
            CompoundTag pending = chunk.pendingSpatial();
            if (pending != null && pending.contains(tag)) {
                chunkTag.put(tag, pending.get(tag));
            }
            return;
        }
        ListTag list = new ListTag();
        ModuleSystem modules = EmpiricCore.modules();
        forEachIn(chunk, attachment -> {
            if (modules.countSaved(attachment, ModuleType::persists) == 0) {
                return;
            }
            CompoundTag entry = new CompoundTag();
            writer.accept(attachment, entry);
            entry.putByteArray("modules", WorldModuleSystem.encode(attachment));
            list.add(entry);
        });
        if (!list.isEmpty()) {
            chunkTag.put(tag, list);
        }
    }

    public void load(ChunkAttachment chunk, ListTag list) {
        ModuleSystem modules = EmpiricCore.modules();
        Level level = chunk.level();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            A attachment = reader.apply(level, entry);
            track(chunk, attachment);
            modules.load(attachment, EmpiricBuffers.buffer(entry.getByteArray("modules")), modules.specs().context(Encoding.KEYED));
        }
    }

    public void unload(ChunkAttachment chunk) {
        ModuleSystem modules = EmpiricCore.modules();
        List<A> attachments = new ArrayList<>();
        forEachIn(chunk, attachments::add);
        for (A attachment : attachments) {
            modules.unload(attachment);
            map(attachment.level()).remove(attachment.x(), attachment.y(), attachment.z());
        }
        chunk.spatial(this).clear();
    }

    public void forEach(Side side, Consumer<A> action) {
        if (side == Side.CLIENT) {
            Level level = ClientLookup.level();
            if (level != null) {
                map(level).forEach((x, y, z, attachment) -> action.accept(attachment));
            }
            return;
        }
        MinecraftServer server = WorldModuleSystem.server();
        if (side == Side.SERVER && server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                map(level).forEach((x, y, z, attachment) -> action.accept(attachment));
            }
        }
    }

    public static CompoundTag readAll(CompoundTag chunkTag) {
        CompoundTag pending = null;
        for (SpatialModuleStore<?> store : STORES) {
            if (chunkTag.contains(store.tag, Tag.TAG_LIST)) {
                if (pending == null) {
                    pending = new CompoundTag();
                }
                pending.put(store.tag, chunkTag.getList(store.tag, Tag.TAG_COMPOUND));
            }
        }
        return pending;
    }

    public static void saveAll(ChunkAttachment chunk, CompoundTag chunkTag) {
        for (SpatialModuleStore<?> store : STORES) {
            store.save(chunk, chunkTag);
        }
    }

    public static void loadAll(ChunkAttachment chunk) {
        CompoundTag pending = chunk.pendingSpatial();
        if (pending == null) {
            return;
        }
        chunk.pendingSpatial(null);
        for (SpatialModuleStore<?> store : STORES) {
            if (pending.contains(store.tag, Tag.TAG_LIST)) {
                store.load(chunk, pending.getList(store.tag, Tag.TAG_COMPOUND));
            }
        }
    }

    public static void unloadAll(ChunkAttachment chunk) {
        for (SpatialModuleStore<?> store : STORES) {
            store.unload(chunk);
        }
    }
}
