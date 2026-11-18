package xyz.volcanobay.empiric.api.module;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.resources.ResourceLocation;
import xyz.volcanobay.empiric.api.attachment.SubLevelAttachment;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleId;
import xyz.volcanobay.empiric.core.module.ModuleType;

import java.util.function.Supplier;

/**
 * Module type for Sable sub-levels. Only register when {@link xyz.volcanobay.empiric.platform.ModCompat#SABLE} is true.
 */
public class SubLevelModuleType<M extends Module<SubLevelAttachment>> extends ModuleType<M, SubLevelAttachment> {

    protected SubLevelModuleType(ModuleType.Builder<M, SubLevelAttachment, ?> builder) {
        super(builder);
    }

    public static <M extends Module<SubLevelAttachment>> Builder<M> builder(ResourceLocation id, Class<M> moduleClass, Supplier<M> factory) {
        return new Builder<>(id::toString, moduleClass, factory);
    }

    public static <M extends Module<SubLevelAttachment>> Builder<M> builder(Class<M> moduleClass, Supplier<M> factory) {
        return new Builder<>(null, moduleClass, factory);
    }

    public M get(SubLevel subLevel) {
        return get(SubLevelAttachment.of(subLevel));
    }

    public M attach(SubLevel subLevel) {
        return attach(SubLevelAttachment.of(subLevel));
    }

    public boolean detach(SubLevel subLevel) {
        return detach(SubLevelAttachment.of(subLevel));
    }

    public static final class Builder<M extends Module<SubLevelAttachment>> extends ModuleType.Builder<M, SubLevelAttachment, SubLevelModuleType<M>> {

        private Builder(ModuleId id, Class<M> moduleClass, Supplier<M> factory) {
            super(id, moduleClass, SubLevelAttachment.TYPE, factory, SubLevelModuleType::new);
        }
    }
}
