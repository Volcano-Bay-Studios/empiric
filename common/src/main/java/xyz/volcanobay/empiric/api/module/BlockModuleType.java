package xyz.volcanobay.empiric.api.module;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.attachment.BlockAttachment;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleId;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.system.BlockModuleSystem;

import java.util.function.Supplier;

public class BlockModuleType<M extends Module<BlockAttachment>> extends ModuleType<M, BlockAttachment> {

    protected BlockModuleType(ModuleType.Builder<M, BlockAttachment, ?> builder) {
        super(builder);
    }

    public static <M extends Module<BlockAttachment>> Builder<M> builder(ResourceLocation id, Class<M> moduleClass, Supplier<M> factory) {
        return new Builder<>(id::toString, moduleClass, factory);
    }

    public static <M extends Module<BlockAttachment>> Builder<M> builder(Class<M> moduleClass, Supplier<M> factory) {
        return new Builder<>(null, moduleClass, factory);
    }

    public M get(Level level, BlockPos pos) {
        return get(BlockAttachment.find(level, pos));
    }

    public M attach(Level level, BlockPos pos) {
        return attach(BlockAttachment.get(level, pos));
    }

    public boolean detach(Level level, BlockPos pos) {
        return detach(BlockAttachment.find(level, pos));
    }

    @Override
    public boolean detach(BlockAttachment attachment) {
        boolean detached = super.detach(attachment);
        if (attachment != null) {
            BlockModuleSystem.prune(attachment);
        }
        return detached;
    }

    public static final class Builder<M extends Module<BlockAttachment>> extends ModuleType.Builder<M, BlockAttachment, BlockModuleType<M>> {

        private Builder(ModuleId id, Class<M> moduleClass, Supplier<M> factory) {
            super(id, moduleClass, EmpiricAttachments.BLOCK, factory, BlockModuleType::new);
        }
    }
}
