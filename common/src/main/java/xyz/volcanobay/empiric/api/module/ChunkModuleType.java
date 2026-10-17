package xyz.volcanobay.empiric.api.module;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleType;

import java.util.function.Supplier;

public class ChunkModuleType<M extends Module<ChunkAttachment>> extends ModuleType<M, ChunkAttachment> {

    protected ChunkModuleType(Builder<M, ChunkAttachment, ?> builder) {
        super(builder);
    }

    public static <M extends Module<ChunkAttachment>> Builder<M, ChunkAttachment, ChunkModuleType<M>> builder(ResourceLocation id, Class<M> moduleClass, Supplier<M> factory) {
        return ModuleType.builder(id::toString, moduleClass, EmpiricAttachments.CHUNK, factory, ChunkModuleType::new);
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
}
