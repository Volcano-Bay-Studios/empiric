package xyz.volcanobay.empiric.registry.adapter;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.registry.EntityAttributes;
import xyz.volcanobay.empiric.api.registry.EntityProps;
import xyz.volcanobay.empiric.core.registry.RegistrationEntry;
import xyz.volcanobay.empiric.registry.EmpiricRegistration;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Supplier;

public class EntityTypeAdapter extends VanillaAdapter<EntityType<?>> {

    public EntityTypeAdapter() {
        super(Registries.ENTITY_TYPE, generic(EntityType.class));
    }

    @Override
    public List<Class<?>> sourceTypes() {
        return List.of(EntityType.class, Entity.class);
    }

    @Override
    public Type producedType(RegistrationEntry entry) {
        if (entry.source() == RegistrationEntry.Kind.CLASS) {
            return new AdapterSupport.Generic(EntityType.class, entry.sourceClass());
        }
        return super.producedType(entry);
    }

    @Override
    public List<Class<? extends Annotation>> hookAnnotations() {
        return List.of(EntityProps.class, EntityAttributes.class);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public EntityType<?> create(RegistrationEntry entry) {
        Class<?> type = AdapterSupport.instanceClass(entry);
        if (type == null) {
            return super.create(entry);
        }
        AdapterSupport.requireNamed(type, entry, "EntityType");
        Constructor<?> constructor;
        try {
            constructor = type.getDeclaredConstructor(EntityType.class, Level.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(type.getName() + " needs a constructor that takes (EntityType, Level)", e);
        }
        EntityType.EntityFactory<Entity> factory = AdapterSupport.factory(EntityType.EntityFactory.class, MethodType.methodType(Entity.class, EntityType.class, Level.class), constructor);
        if (factory == null) {
            MethodHandle handle = AdapterSupport.handle(constructor);
            factory = (entityType, level) -> {
                try {
                    return (Entity) handle.invoke(entityType, level);
                } catch (Throwable throwable) {
                    throw AdapterSupport.rethrow(throwable);
                }
            };
        }
        EntityType.Builder builder = EntityType.Builder.of(factory, MobCategory.MISC);
        EntityType.Builder configured = entry.hook(EntityProps.class, EntityType.Builder.class, builder);
        if (configured != null) {
            builder = configured;
        }
        return builder.build(entry.id());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void register(RegistrationEntry entry, Supplier<EntityType<?>> value) {
        Supplier<EntityType<?>> type = registerValue(entry, value);
        Class<?> instanceClass = AdapterSupport.instanceClass(entry);
        if (instanceClass == null) {
            return;
        }
        if (entry.source() == RegistrationEntry.Kind.CLASS && LivingEntity.class.isAssignableFrom(instanceClass)) {
            Supplier<?> living = type;
            EmpiricRegistration.addAttributes((Supplier<EntityType<? extends LivingEntity>>) living, () -> entry.hook(EntityAttributes.class, AttributeSupplier.Builder.class));
        }
        EmpiricRegistration.addRenderer(instanceClass, type, true);
    }
}
