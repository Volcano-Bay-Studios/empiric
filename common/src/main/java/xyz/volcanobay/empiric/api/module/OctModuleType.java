package xyz.volcanobay.empiric.api.module;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.api.attachment.OctAttachment;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleId;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.system.OctModuleSystem;

import java.util.function.Supplier;

public class OctModuleType<M extends Module<OctAttachment>> extends ModuleType<M, OctAttachment> {

    protected OctModuleType(ModuleType.Builder<M, OctAttachment, ?> builder) {
        super(builder);
    }

    public static <M extends Module<OctAttachment>> Builder<M> builder(ResourceLocation id, Class<M> moduleClass, Supplier<M> factory) {
        return new Builder<>(id::toString, moduleClass, factory);
    }

    public static <M extends Module<OctAttachment>> Builder<M> builder(Class<M> moduleClass, Supplier<M> factory) {
        return new Builder<>(null, moduleClass, factory);
    }

    public M get(Level level, OctPos pos) {
        return get(OctAttachment.find(level, pos));
    }

    public M attach(Level level, OctPos pos) {
        return attach(OctAttachment.get(level, pos));
    }

    public boolean detach(Level level, OctPos pos) {
        return detach(OctAttachment.find(level, pos));
    }

    @Override
    public boolean detach(OctAttachment attachment) {
        boolean detached = super.detach(attachment);
        if (attachment != null) {
            OctModuleSystem.prune(attachment);
        }
        return detached;
    }

    public static final class Builder<M extends Module<OctAttachment>> extends ModuleType.Builder<M, OctAttachment, OctModuleType<M>> {

        private Builder(ModuleId id, Class<M> moduleClass, Supplier<M> factory) {
            super(id, moduleClass, EmpiricAttachments.OCT, factory, OctModuleType::new);
        }
    }
}
