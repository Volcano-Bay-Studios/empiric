package xyz.volcanobay.empiric.api.module;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.api.attachment.ServerAttachment;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleId;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.core.module.Side;

import java.util.function.Supplier;

public class ServerModuleType<M extends Module<ServerAttachment>> extends ModuleType<M, ServerAttachment> {

    protected ServerModuleType(ModuleType.Builder<M, ServerAttachment, ?> builder) {
        super(builder);
    }

    public static <M extends Module<ServerAttachment>> Builder<M> builder(ResourceLocation id, Class<M> moduleClass, Supplier<M> factory) {
        return new Builder<>(id::toString, moduleClass, factory);
    }

    public static <M extends Module<ServerAttachment>> Builder<M> builder(Class<M> moduleClass, Supplier<M> factory) {
        return new Builder<>(null, moduleClass, factory);
    }

    public M get(MinecraftServer server) {
        return get(ServerAttachment.of(Side.SERVER));
    }

    public M get(Level level) {
        return get(ServerAttachment.of(level));
    }

    public M attach(MinecraftServer server) {
        return attach(ServerAttachment.of(Side.SERVER));
    }

    public boolean detach(MinecraftServer server) {
        return detach(ServerAttachment.of(Side.SERVER));
    }

    public static final class Builder<M extends Module<ServerAttachment>> extends ModuleType.Builder<M, ServerAttachment, ServerModuleType<M>> {

        private Builder(ModuleId id, Class<M> moduleClass, Supplier<M> factory) {
            super(id, moduleClass, EmpiricAttachments.SERVER, factory, ServerModuleType::new);
        }
    }
}
