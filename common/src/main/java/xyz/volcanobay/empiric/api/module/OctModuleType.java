package xyz.volcanobay.empiric.api.module;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.api.attachment.OctAttachment;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.system.OctModuleSystem;

import java.util.function.Supplier;

public class OctModuleType<M extends Module<OctAttachment>> extends ModuleType<M, OctAttachment> {

    protected OctModuleType(Builder<M, OctAttachment, ?> builder) {
        super(builder);
    }

    public static <M extends Module<OctAttachment>> Builder<M, OctAttachment, OctModuleType<M>> builder(ResourceLocation id, Class<M> moduleClass, Supplier<M> factory) {
        return ModuleType.builder(id::toString, moduleClass, EmpiricAttachments.OCT, factory, OctModuleType::new);
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
}
