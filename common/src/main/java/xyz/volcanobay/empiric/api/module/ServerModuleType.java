package xyz.volcanobay.empiric.api.module;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.api.attachment.ServerAttachment;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.core.module.Side;

import java.util.function.Supplier;

public class ServerModuleType<M extends Module<ServerAttachment>> extends ModuleType<M, ServerAttachment> {

    protected ServerModuleType(Builder<M, ServerAttachment, ?> builder) {
        super(builder);
    }

    public static <M extends Module<ServerAttachment>> Builder<M, ServerAttachment, ServerModuleType<M>> builder(ResourceLocation id, Class<M> moduleClass, Supplier<M> factory) {
        return ModuleType.builder(id::toString, moduleClass, EmpiricAttachments.SERVER, factory, ServerModuleType::new);
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
}
