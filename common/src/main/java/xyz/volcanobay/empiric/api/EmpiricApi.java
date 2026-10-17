package xyz.volcanobay.empiric.api;

import net.minecraft.resources.ResourceLocation;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.attachment.Attachment;
import xyz.volcanobay.empiric.core.attachment.AttachmentType;
import xyz.volcanobay.empiric.core.data.SpecRegistry;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleEvent;
import xyz.volcanobay.empiric.core.module.ModuleEventListener;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.ModuleType;

import java.util.Set;
import java.util.function.Supplier;

public final class EmpiricApi {

    private EmpiricApi() {
    }

    public static ModuleSystem modules() {
        return EmpiricCore.modules();
    }

    public static SpecRegistry specs() {
        return EmpiricCore.specs();
    }

    public static <M extends Module<A>, A extends Attachment> ModuleType.Builder<M, A, ModuleType<M, A>> moduleType(ResourceLocation id, Class<M> moduleClass, AttachmentType<A, ?> attachmentType, Supplier<M> factory) {
        return ModuleType.builder(id::toString, moduleClass, attachmentType, factory);
    }

    public static ModuleType<?, ?> moduleType(ResourceLocation id) {
        return modules().type(id.toString());
    }

    public static String id(ResourceLocation location) {
        return location.toString();
    }

    public static boolean subscribe(String modId, ModuleType<?, ?> type) {
        return modules().subscribe(modId, type);
    }

    public static boolean subscribe(String modId, ResourceLocation type) {
        return modules().subscribe(modId, type.toString());
    }

    public static boolean unsubscribe(String modId, ModuleType<?, ?> type) {
        return modules().unsubscribe(modId, type);
    }

    public static boolean unsubscribe(String modId, ResourceLocation type) {
        return modules().unsubscribe(modId, type.toString());
    }

    public static <E> void listen(ModuleEvent<E> event, ModuleEventListener<Module<?>, ? super E> listener) {
        modules().listen(event, listener);
    }

    public static <M extends Module<A>, A extends Attachment, E> void listen(String modId, ModuleType<M, A> type, ModuleEvent<E> event, ModuleEventListener<? super M, ? super E> listener) {
        type.listen(modId, event, listener);
    }

    public static boolean unlisten(String modId, ModuleType<?, ?> type) {
        return type.unlisten(modId);
    }

    public static boolean isEnabled(ModuleType<?, ?> type) {
        return type.isEnabled();
    }

    public static Set<String> subscribers(ModuleType<?, ?> type) {
        return modules().subscribers(type);
    }
}
