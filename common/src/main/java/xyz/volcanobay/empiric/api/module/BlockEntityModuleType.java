package xyz.volcanobay.empiric.api.module;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import xyz.volcanobay.empiric.api.attachment.BlockEntityAttachment;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleId;
import xyz.volcanobay.empiric.core.module.ModuleType;

import java.util.function.Supplier;

public class BlockEntityModuleType<M extends Module<BlockEntityAttachment>> extends ModuleType<M, BlockEntityAttachment> {

    protected BlockEntityModuleType(ModuleType.Builder<M, BlockEntityAttachment, ?> builder) {
        super(builder);
    }

    public static <M extends Module<BlockEntityAttachment>> Builder<M> builder(ResourceLocation id, Class<M> moduleClass, Supplier<M> factory) {
        return new Builder<>(id::toString, moduleClass, factory);
    }

    public static <M extends Module<BlockEntityAttachment>> Builder<M> builder(Class<M> moduleClass, Supplier<M> factory) {
        return new Builder<>(null, moduleClass, factory);
    }

    public M get(BlockEntity blockEntity) {
        return get(BlockEntityAttachment.of(blockEntity));
    }

    public M attach(BlockEntity blockEntity) {
        return attach(BlockEntityAttachment.of(blockEntity));
    }

    public boolean detach(BlockEntity blockEntity) {
        return detach(BlockEntityAttachment.of(blockEntity));
    }

    public static final class Builder<M extends Module<BlockEntityAttachment>> extends ModuleType.Builder<M, BlockEntityAttachment, BlockEntityModuleType<M>> {

        private Builder(ModuleId id, Class<M> moduleClass, Supplier<M> factory) {
            super(id, moduleClass, EmpiricAttachments.BLOCK_ENTITY, factory, BlockEntityModuleType::new);
        }
    }
}
