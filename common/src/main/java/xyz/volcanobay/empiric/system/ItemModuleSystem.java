package xyz.volcanobay.empiric.system;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.api.attachment.ItemStackAttachment;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.registry.EmpiricDataComponents;
import xyz.volcanobay.empiric.core.data.Encoding;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.data.EmpiricBuffers;
import xyz.volcanobay.empiric.data.ItemModules;

import java.util.function.Consumer;

public final class ItemModuleSystem {

    private ItemModuleSystem() {
    }

    public static ItemStackAttachment open(ItemStack stack) {
        ItemStackAttachment attachment = new ItemStackAttachment(stack);
        ItemModules modules = stack.get(EmpiricDataComponents.MODULES.get());
        if (modules != null) {
            ModuleSystem system = EmpiricCore.modules();
            system.load(attachment, EmpiricBuffers.buffer(modules.data()), system.specs().context(Encoding.KEYED));
        }
        return attachment;
    }

    public static void close(ItemStackAttachment attachment) {
        ItemStack stack = attachment.stack();
        if (attachment.modules().isEmpty()) {
            stack.remove(EmpiricDataComponents.MODULES.get());
            return;
        }
        ModuleSystem system = EmpiricCore.modules();
        RegistryFriendlyByteBuf buffer = EmpiricBuffers.create();
        system.save(attachment, EmpiricBuffers.of(buffer), system.specs().context(Encoding.KEYED));
        stack.set(EmpiricDataComponents.MODULES.get(), new ItemModules(EmpiricBuffers.bytes(buffer)));
    }

    public static <M extends Module<ItemStackAttachment>> M read(ItemStack stack, ModuleType<M, ItemStackAttachment> type) {
        if (!stack.has(EmpiricDataComponents.MODULES.get())) {
            return null;
        }
        return open(stack).modules().get(type);
    }

    public static <M extends Module<ItemStackAttachment>> M modify(ItemStack stack, ModuleType<M, ItemStackAttachment> type, Consumer<M> action) {
        ItemStackAttachment attachment = open(stack);
        M module = EmpiricCore.modules().attach(attachment, type);
        action.accept(module);
        close(attachment);
        return module;
    }

    public static <M extends Module<ItemStackAttachment>> boolean remove(ItemStack stack, ModuleType<M, ItemStackAttachment> type) {
        if (!stack.has(EmpiricDataComponents.MODULES.get())) {
            return false;
        }
        ItemStackAttachment attachment = open(stack);
        boolean removed = EmpiricCore.modules().detach(attachment, type);
        close(attachment);
        return removed;
    }
}
