package xyz.volcanobay.empiric.api.attachment;

import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.core.attachment.Attachment;
import xyz.volcanobay.empiric.core.attachment.AttachmentType;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.DataContext;
import xyz.volcanobay.empiric.core.module.ModuleHolder;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.system.ItemModuleSystem;

public final class ItemStackAttachment implements Attachment {

    private final ItemStack stack;
    private final ModuleHolder modules = new ModuleHolder(this);

    public ItemStackAttachment(ItemStack stack) {
        this.stack = stack;
    }

    public ItemStack stack() {
        return stack;
    }

    @Override
    public AttachmentType<?, ?> attachmentType() {
        return EmpiricAttachments.ITEM_STACK;
    }

    @Override
    public ModuleHolder modules() {
        return modules;
    }

    @Override
    public Side side() {
        return Side.NONE;
    }

    public static final class Type extends AttachmentType<ItemStackAttachment, ItemStack> {

        Type() {
            super("empiric:item_stack");
        }

        @Override
        public ItemStackAttachment get(ItemStack stack) {
            return ItemModuleSystem.open(stack);
        }

        @Override
        public void writeKey(DataBuffer buffer, ItemStackAttachment attachment, DataContext context) {
            throw new UnsupportedOperationException("Item stack modules sync through their data component");
        }

        @Override
        public ItemStackAttachment readKey(DataBuffer buffer, Side side, DataContext context) {
            throw new UnsupportedOperationException("Item stack modules sync through their data component");
        }
    }
}
