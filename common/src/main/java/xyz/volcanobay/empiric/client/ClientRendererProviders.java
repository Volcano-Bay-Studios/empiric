package xyz.volcanobay.empiric.client;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * Builds renderer providers from annotated class names.
 * Note: Reflection here runs only during startup registration.
 */
public final class ClientRendererProviders {

    private ClientRendererProviders() {
    }

    public static EntityRendererProvider<Entity> entity(String className) {
        MethodHandle handle = constructor(className, EntityRendererProvider.Context.class);
        return context -> (EntityRenderer<Entity>) invoke(handle, context);
    }

    public static BlockEntityRendererProvider<BlockEntity> blockEntity(String className) {
        MethodHandle handle = constructor(className, BlockEntityRendererProvider.Context.class);
        return context -> (BlockEntityRenderer<BlockEntity>) invoke(handle, context);
    }

    private static MethodHandle constructor(String className, Class<?> context) {
        try {
            Class<?> type = Class.forName(className, true, ClientRendererProviders.class.getClassLoader());
            return MethodHandles.lookup().unreflectConstructor(type.getConstructor(context));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Can't use " + className + " as a renderer, it needs a constructor that takes a " + context.getSimpleName(), e);
        }
    }

    private static Object invoke(MethodHandle handle, Object context) {
        try {
            return handle.invoke(context);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }
}
