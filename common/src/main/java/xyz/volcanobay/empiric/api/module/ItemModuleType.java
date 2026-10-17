package xyz.volcanobay.empiric.api.module;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.api.attachment.ItemStackAttachment;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.system.ItemModuleSystem;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ItemModuleType<M extends Module<ItemStackAttachment>> extends ModuleType<M, ItemStackAttachment> {

    protected ItemModuleType(Builder<M, ItemStackAttachment, ?> builder) {
        super(builder);
    }

    public static <M extends Module<ItemStackAttachment>> Builder<M, ItemStackAttachment, ItemModuleType<M>> builder(ResourceLocation id, Class<M> moduleClass, Supplier<M> factory) {
        return ModuleType.builder(id::toString, moduleClass, EmpiricAttachments.ITEM_STACK, factory, ItemModuleType::new);
    }

    public M get(ItemStack stack) {
        return ItemModuleSystem.read(stack, this);
    }

    public M modify(ItemStack stack, Consumer<M> action) {
        return ItemModuleSystem.modify(stack, this, action);
    }

    public boolean remove(ItemStack stack) {
        return ItemModuleSystem.remove(stack, this);
    }
}
