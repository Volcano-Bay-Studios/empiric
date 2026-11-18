package xyz.volcanobay.empiric.api.module;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.api.attachment.EntityAttachment;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleId;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

import java.util.function.Supplier;

public class EntityModuleType<M extends Module<EntityAttachment>> extends ModuleType<M, EntityAttachment> {

    protected EntityModuleType(ModuleType.Builder<M, EntityAttachment, ?> builder) {
        super(builder);
    }

    public static <M extends Module<EntityAttachment>> Builder<M> builder(ResourceLocation id, Class<M> moduleClass, Supplier<M> factory) {
        return new Builder<>(id::toString, moduleClass, factory);
    }

    public static <M extends Module<EntityAttachment>> Builder<M> builder(Class<M> moduleClass, Supplier<M> factory) {
        return new Builder<>(null, moduleClass, factory);
    }

    public M get(Entity entity) {
        return get(EntityAttachment.of(entity));
    }

    public M attach(Entity entity) {
        return attach(EntityAttachment.of(entity));
    }

    public boolean detach(Entity entity) {
        return detach(EntityAttachment.of(entity));
    }

    public boolean drop(Entity entity) {
        return detach(entity);
    }

    public M copy(Entity from, Entity to) {
        EntityAttachment source = EntityAttachment.of(from);
        EntityAttachment target = EntityAttachment.of(to);
        if (get(source) == null || !target.isActive()) {
            return null;
        }
        detach(target);
        WorldModuleSystem.copy(source, target, type -> type == this);
        return get(target);
    }

    public static final class Builder<M extends Module<EntityAttachment>> extends ModuleType.Builder<M, EntityAttachment, EntityModuleType<M>> {

        private Builder(ModuleId id, Class<M> moduleClass, Supplier<M> factory) {
            super(id, moduleClass, EmpiricAttachments.ENTITY, factory, EntityModuleType::new);
        }
    }
}
