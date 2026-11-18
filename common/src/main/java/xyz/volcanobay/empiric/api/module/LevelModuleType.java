package xyz.volcanobay.empiric.api.module;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.api.attachment.LevelAttachment;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleId;
import xyz.volcanobay.empiric.core.module.ModuleType;

import java.util.function.Supplier;

public class LevelModuleType<M extends Module<LevelAttachment>> extends ModuleType<M, LevelAttachment> {

    protected LevelModuleType(ModuleType.Builder<M, LevelAttachment, ?> builder) {
        super(builder);
    }

    public static <M extends Module<LevelAttachment>> Builder<M> builder(ResourceLocation id, Class<M> moduleClass, Supplier<M> factory) {
        return new Builder<>(id::toString, moduleClass, factory);
    }

    public static <M extends Module<LevelAttachment>> Builder<M> builder(Class<M> moduleClass, Supplier<M> factory) {
        return new Builder<>(null, moduleClass, factory);
    }

    public M get(Level level) {
        return get(LevelAttachment.of(level));
    }

    public M attach(Level level) {
        return attach(LevelAttachment.of(level));
    }

    public boolean detach(Level level) {
        return detach(LevelAttachment.of(level));
    }

    public static final class Builder<M extends Module<LevelAttachment>> extends ModuleType.Builder<M, LevelAttachment, LevelModuleType<M>> {

        private Builder(ModuleId id, Class<M> moduleClass, Supplier<M> factory) {
            super(id, moduleClass, EmpiricAttachments.LEVEL, factory, LevelModuleType::new);
        }
    }
}
