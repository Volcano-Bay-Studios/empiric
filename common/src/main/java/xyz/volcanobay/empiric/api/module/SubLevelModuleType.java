package xyz.volcanobay.empiric.api.module;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.resources.ResourceLocation;
import xyz.volcanobay.empiric.api.attachment.SubLevelAttachment;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleType;

import java.util.function.Supplier;

/**
 * Module type for Sable sub-levels. Only register when {@link xyz.volcanobay.empiric.platform.ModCompat#SABLE} is true.
 */
public class SubLevelModuleType<M extends Module<SubLevelAttachment>> extends ModuleType<M, SubLevelAttachment> {

    protected SubLevelModuleType(Builder<M, SubLevelAttachment, ?> builder) {
        super(builder);
    }

    public static <M extends Module<SubLevelAttachment>> Builder<M, SubLevelAttachment, SubLevelModuleType<M>> builder(ResourceLocation id, Class<M> moduleClass, Supplier<M> factory) {
        return ModuleType.builder(id::toString, moduleClass, SubLevelAttachment.TYPE, factory, SubLevelModuleType::new);
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
}
