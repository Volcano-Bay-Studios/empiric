package xyz.volcanobay.empiric.api.module;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleId;
import xyz.volcanobay.empiric.core.module.ModuleType;

import java.util.function.Supplier;

public class ChunkModuleType<M extends Module<ChunkAttachment>> extends ModuleType<M, ChunkAttachment> {

    protected ChunkModuleType(ModuleType.Builder<M, ChunkAttachment, ?> builder) {
        super(builder);
    }

    public static <M extends Module<ChunkAttachment>> Builder<M> builder(ResourceLocation id, Class<M> moduleClass, Supplier<M> factory) {
        return new Builder<>(id::toString, moduleClass, factory);
    }

    public static <M extends Module<ChunkAttachment>> Builder<M> builder(Class<M> moduleClass, Supplier<M> factory) {
        return new Builder<>(null, moduleClass, factory);
    }

    public M get(LevelChunk chunk) {
        return get(ChunkAttachment.of(chunk));
    }

    public M attach(LevelChunk chunk) {
        return attach(ChunkAttachment.of(chunk));
    }

    public boolean detach(LevelChunk chunk) {
        return detach(ChunkAttachment.of(chunk));
    }

    public static final class Builder<M extends Module<ChunkAttachment>> extends ModuleType.Builder<M, ChunkAttachment, ChunkModuleType<M>> {

        private Builder(ModuleId id, Class<M> moduleClass, Supplier<M> factory) {
            super(id, moduleClass, EmpiricAttachments.CHUNK, factory, ChunkModuleType::new);
        }
    }
}
