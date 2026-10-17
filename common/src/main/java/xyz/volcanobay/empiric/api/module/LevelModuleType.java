package xyz.volcanobay.empiric.api.module;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.api.attachment.LevelAttachment;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleType;

import java.util.function.Supplier;

public class LevelModuleType<M extends Module<LevelAttachment>> extends ModuleType<M, LevelAttachment> {

    protected LevelModuleType(Builder<M, LevelAttachment, ?> builder) {
        super(builder);
    }

    public static <M extends Module<LevelAttachment>> Builder<M, LevelAttachment, LevelModuleType<M>> builder(ResourceLocation id, Class<M> moduleClass, Supplier<M> factory) {
        return ModuleType.builder(id::toString, moduleClass, EmpiricAttachments.LEVEL, factory, LevelModuleType::new);
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
}
